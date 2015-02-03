package models

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.datastax.driver.core.{ResultSet, Row}
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.Iteratee
import helpers._
import models.cassandra.Cassandra
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
case class User(
  id: UUID = UUIDs.timeBased(),
  name: String = "",
  salt: String = "",
  encrypted_password: String = "",
  email: String = "",
  password: String = "",
  remember_me: Boolean = false
) extends TimeBased {

  def hasPassword(submittedPasswd: String) = {
    if (encrypted_password == "") false
    else encrypted_password == encrypt(salt, submittedPasswd)
  }

  def encryptPassword = {
    val salt =
      if (hasPassword(password)) this.salt
      else makeSalt(password)

    this.copy(
      salt = salt,
      encrypted_password = encrypt(salt, password)
    )
  }

  private def encrypt(salt: String, passwd: String) = sha2(s"$salt--$passwd")

  private def makeSalt(passwd: String) = sha2(s"${DateTime.now}--$passwd")

  import scala.language.postfixOps

  def sha2(text: String): String = {
    import java.security.MessageDigest

    val digest = MessageDigest.getInstance("SHA-256")
    digest.reset()
    digest.update(text.getBytes)
    digest.digest().map(0xFF &)
      .map {"%02x".format(_)}.foldLeft("") {_ + _}
  }
}

/**
 *
 */
sealed class Users extends CassandraTable[Users, User] {

  override val tableName = "users"

  object id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object name extends StringColumn(this)

  object salt extends StringColumn(this)

  object encrypted_password extends StringColumn(this)

  object email extends StringColumn(this)

  override def fromRow(r: Row): User = {
    User(id(r), name(r), salt(r), encrypted_password(r), email(r), "", remember_me = false)
  }
}

object User extends Users with Logging with Cassandra {

  Await.result(create.future(), 500 millis)
  Await.result(UserByEmail.create.future(), 500 millis)

  case class NotFound(user: String)
    extends BaseException("not.found.user")

  case class WrongPassword(email: String)
    extends BaseException("password.wrong")

  case class AuthFailed(id: UUID)
    extends BaseException("auth.failed")

  case class NoCredentials()
    extends BaseException("no.credentials")

  def findBy(id: UUID): Future[User] = {
    select.where(_.id eqs id).one().map {
      case None    => throw NotFound(id.toString)
      case Some(u) => u
    }
  }

  def findBy(email: String): Future[User] = {
    UserByEmail.find(email).flatMap {case (_, id) => findBy(id)}
  }

  /**
   *
   * @param user user
   * @return
   */
  def save(user: User): Future[User] = {
    val u = user.encryptPassword
    BatchStatement()
      .add(UserByEmail.cql_save(u.email, u.id))
      .add(User.cql_save(u))
      .future().map {_ => u}
  }

  private def cql_save(u: User) = {
    insert.value(_.id, u.id)
      .value(_.name, u.name)
      .value(_.salt, u.salt)
      .value(_.encrypted_password, u.encrypted_password)
      .value(_.email, u.email)
  }

  def updateName(id: UUID, name: String): Future[ResultSet] = {
    update.where(_.id eqs id).modify(_.name setTo name).future()
  }

  def remove(id: UUID): Future[ResultSet] = {
    findBy(id).flatMap {user =>
      BatchStatement()
        .add(UserByEmail.cql_del(user.email))
        .add(delete.where(_.id eqs id)).future()
    }
  }

  def page(start: UUID, limit: Int): Future[Seq[User]] = {
    select.where(_.id gtToken start).limit(limit).fetch()
  }

  def all: Future[Seq[User]] = {
    select.fetchEnumerator() run Iteratee.collect()
  }

  ////////////////////////////////////////////////////////////////

  case class Credentials(id: UUID, salt: String)

  /**
   *
   * @param cred credentials
   * @return
   */
  def auth(cred: Credentials): Future[User] = {
    findBy(cred.id).map {u =>
      if (u.salt == cred.salt) u
      else throw AuthFailed(cred.id)
    }
  }

  def auth(email: String, passwd: String): Future[User] = {
    findBy(email).map {user =>
      if (user.hasPassword(passwd)) user
      else throw WrongPassword(email)
    }
  }
}

sealed class UserByEmail extends CassandraTable[UserByEmail, (String, UUID)] {

  override val tableName = "users_email_index"

  object email
    extends StringColumn(this)
    with PartitionKey[String]

  object id extends UUIDColumn(this)

  override def fromRow(r: Row): (String, UUID) = (email(r), id(r))
}

object UserByEmail extends UserByEmail with Logging with Cassandra {

  def cql_save(email: String, id: UUID) = {
    update.where(_.email eqs email).modify(_.id setTo id)
  }

  def find(email: String): Future[(String, UUID)] = {
    if (email.isEmpty) throw User.NotFound(email)
    else select.where(_.email eqs email).one().map {
      case None      => throw User.NotFound(email)
      case Some(idx) => idx
    }
  }

  def cql_del(email: String) = {
    delete.where(_.email eqs email)
  }

  def remove(email: String): Future[ResultSet] = {
    cql_del(email).future()
  }
}