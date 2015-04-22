package models

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.datastax.driver.core.{ResultSet, Row}
import com.websudos.phantom.Implicits._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra._
import models.sys.SysConfig
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.Crypto

import scala.collection.TraversableOnce
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
  int_groups: InternalGroups = InternalGroups(0),
  ext_groups: Set[UUID] = Set(),
  password: String = "",
  remember_me: Boolean = false
) extends TimeBased {

  lazy val groups: Set[UUID] = ext_groups union int_groups

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

  def savePassword(newPassword: String): Future[User] = {
    User.savePassword(this, newPassword)
  }

  def save = User.save(this)

  def toUserInfo = UserInfo(id, name, email, int_groups.code)

  private def encrypt(salt: String, passwd: String) =
    Crypto.sha2(s"$salt--$passwd")

  private def makeSalt(passwd: String) =
    Crypto.sha2(s"${DateTime.now}--$passwd")
}

/**
 *
 */
sealed class Users
  extends CassandraTable[Users, User]
  with Module[Users, User]
  with ExtCQL[Users, User]
  with Logging {

  override val tableName = "users"

  object id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object name extends StringColumn(this)

  object salt extends StringColumn(this)

  object encrypted_password extends StringColumn(this)

  object email extends StringColumn(this)

  object internal_groups extends IntColumn(this)

  object external_groups extends SetColumn[Users, User, UUID](this)

  override def fromRow(r: Row): User = {
    User(
      id(r),
      name(r),
      salt(r),
      encrypted_password(r),
      email(r),
      InternalGroups(internal_groups(r)),
      external_groups(r),
      "",
      remember_me = false
    )
  }
}

object User extends Users with Cassandra with SysConfig with AppConfig {

  case class NotFound(user: String)
    extends BaseException(msg_key("not.found"))

  case class WrongPassword(email: String)
    extends BaseException(msg_key("wrong.password"))

  case class AuthFailed(id: UUID)
    extends BaseException(msg_key("auth.failed"))

  case class NoCredentials()
    extends BaseException(msg_key("no.credentials"))

  case class EmailTaken(email: String)
    extends BaseException(msg_key("email.taken"))

  object AccessControl {

    import models.{AccessControl => AC}

    case class Undefined(
      principal: UUID,
      action: String,
      resource: String
    ) extends AC.Undefined[UUID](action, resource, moduleName)

    case class Denied(
      principal: UUID,
      action: String,
      resource: String
    ) extends AC.Denied[UUID](action, resource, moduleName)

    case class Granted(
      principal: UUID,
      action: String,
      resource: String
    ) extends AC.Granted[UUID](action, resource, moduleName)

  }

  lazy val root: UUID = Await.result(getUUID("root_id"), 10 seconds)

  def find(id: UUID): Future[User] = CQL {
    select.where(_.id eqs id)
  }.one().map {
    case None    => throw NotFound(id.toString)
    case Some(u) => u
  }

  def find(email: String): Future[User] = {
    UserByEmail.find(email).flatMap { case (_, id) => find(id) }
  }

  def find(ids: TraversableOnce[UUID]): Future[Map[UUID, User]] = CQL {
    select
      .where(_.id in ids.toSet.toList)
  }.fetch().map(_.map(u => (u.id, u)).toMap)

  def checkEmail(email: String): Future[Boolean] = {
    UserByEmail.cql_check(email).one().map {
      case None => true
      case _    => throw EmailTaken(email)
    }
  }

  /**
   *
   * @param user user
   * @return
   */
  def save(user: User): Future[User] = {
    val u = user.encryptPassword
    for {
      done <- UserByEmail.save(u.email, u.id)
      user <- if (done) CQL {
        insert
          .value(_.id, u.id)
          .value(_.name, u.name)
          .value(_.salt, u.salt)
          .value(_.encrypted_password, u.encrypted_password)
          .value(_.email, u.email)
          .value(_.internal_groups, u.int_groups.code | InternalGroups.AnyoneMask)
          .value(_.external_groups, Set[UUID]())
      }.future().map(_ => u)
      else throw EmailTaken(u.email)
    } yield user
  }

  def savePassword(user: User, newPassword: String): Future[User] = {
    val u = user.copy(password = newPassword).encryptPassword
    CQL {
      update
        .where(_.id eqs u.id)
        .modify(_.salt setTo u.salt)
        .and(_.encrypted_password setTo u.encrypted_password)
    }.future().map(_ => u)
  }

  def saveName(id: UUID, name: String): Future[ResultSet] = CQL {
    update
      .where(_.id eqs id)
      .modify(_.name setTo name)
  }.future()

  def remove(id: UUID): Future[ResultSet] = {
    find(id).flatMap { user =>
      BatchStatement()
        .add(UserByEmail.cql_del(user.email))
        .add(CQL {delete.where(_.id eqs id)}).future()
    }
  }

  def list(pager: Pager): Future[List[User]] = {
    CQL {
      select.setFetchSize(fetchSize())
    }.fetchEnumerator |>>>
      PIteratee.slice[User](pager.start, pager.limit)
  }.map(_.toList)

  def cql_add_group(id: UUID, gid: UUID) = CQL {
    update
      .where(_.id eqs id)
      .modify(_.external_groups add gid)
  }

  def cql_del_group(id: UUID, gid: UUID) = CQL {
    update
      .where(_.id eqs id)
      .modify(_.external_groups remove gid)
  }

  ////////////////////////////////////////////////////////////////

  case class Credentials(id: UUID, salt: String)

  /**
   *
   * @param cred credentials
   * @return
   */
  def auth(cred: Credentials): Future[User] = {
    find(cred.id).map { u =>
      if (u.salt == cred.salt) u
      else throw AuthFailed(cred.id)
    }
  }

  def auth(email: String, passwd: String): Future[User] = {
    find(email).map { user =>
      if (user.hasPassword(passwd)) user
      else throw WrongPassword(email)
    }
  }
}

sealed class UserByEmail
  extends CassandraTable[UserByEmail, (String, UUID)]
  with ExtCQL[UserByEmail, (String, UUID)]
  with Logging {

  override val tableName = "users_email_index"

  object email
    extends StringColumn(this)
    with PartitionKey[String]

  object id extends UUIDColumn(this)

  override def fromRow(r: Row): (String, UUID) = (email(r), id(r))
}

object UserByEmail extends UserByEmail with Cassandra {

  def save(email: String, id: UUID): Future[Boolean] = CQL {
    insert
      .value(_.email, email)
      .value(_.id, id)
      .ifNotExists()
  }.future().map(_.wasApplied())

  def find(email: String): Future[(String, UUID)] = {
    if (email.isEmpty) throw User.NotFound(email)
    else CQL {
      select.where(_.email eqs email)
    }.one().map {
      case None      => throw User.NotFound(email)
      case Some(idx) => idx
    }
  }

  def cql_del(email: String) = CQL {
    delete.where(_.email eqs email)
  }

  def cql_check(email: String) = CQL {
    select(_.id).where(_.email eqs email)
  }

  def remove(email: String): Future[ResultSet] = {
    cql_del(email).future()
  }
}