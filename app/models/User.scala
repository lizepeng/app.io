package models

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.datastax.driver.core.{ResultSet, Row}
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.Iteratee
import helpers._
import helpers.syntax._
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
    User(id(r), name(r), salt(r), encrypted_password(r), email(r), "", false)
  }
}

object User extends Users with Logging with Cassandra {

  Await.result(create.future(), 500 millis)
  Await.result(UserByEmail.create.future(), 500 millis)

  def findBy(id: UUID): Future[Option[User]] = {
    select.where(_.id eqs id).one()
  }

  def findBy(email: String): Future[Option[User]] = {
    UserByEmail.find(email).flatMap {
      case Some((_, id)) => findBy(id)
      case None          => Future.successful(None)
    }
  }

  /**
   *
   * @param user
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
    findBy(id).foreach {
      case Some(u) => UserByEmail.remove(u.email)
      case None    =>
    }
    delete.where(_.id eqs id).future()
  }

  def page(start: UUID, limit: Int): Future[Seq[User]] = {
    select.where(_.id gtToken start).limit(limit).fetch()
  }

  def all: Future[Seq[User]] = {
    select.fetchEnumerator() run Iteratee.collect()
  }

  ////////////////////////////////////////////////////////////////

  case class Credentials(id: String, salt: String)

  object Credentials {
    def apply(userId: Option[String], salt: Option[String]): Option[Credentials] = {
      if (userId.isEmpty || salt.isEmpty) None
      else Some(Credentials(userId.get, salt.get))
    }
  }

  /**
   *
   * @param cred
   * @return
   */
  def auth(cred: Credentials): Future[Option[User]] = {
    findBy(UUID.fromString(cred.id)).map {
      _.filter(_.salt == cred.salt)
    }
  }

  def auth(email: String, passwd: String): Future[Either[BaseException, User]] = {
    import helpers.ErrorCode._

    findBy(email).map {userOpt =>
      userOpt.toRight {
        NotFoundException(UserNotFound.log(email))
      }.right.flatMap {user =>
        user.hasPassword(passwd).option(user).toRight {
          AuthFailedException(WrongPassword.log(email))
        }
      }
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

  def find(email: String): Future[Option[(String, UUID)]] = {
    if (email.isEmpty) Future.successful(None)
    else select.where(_.email eqs email).one()
  }

  def remove(email: String): Future[ResultSet] = {
    delete.where(_.email eqs email).future()
  }
}