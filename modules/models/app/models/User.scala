package models

import java.util.UUID

import com.websudos.phantom.dsl._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers.ExtCrypto._
import helpers._
import models.cassandra._
import models.sys.{SysConfig, SysConfigs}
import org.joda.time.DateTime
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._

import scala.collection.TraversableOnce
import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class User(
  id: UUID,
  name: Name = Name.empty,
  salt: String = "",
  encrypted_password: String = "",
  email: EmailAddress = EmailAddress.empty,
  internal_groups_code: InternalGroupsCode = InternalGroupsCode(0),
  external_groups: Set[UUID] = Set(),
  password: Password = Password.empty,
  remember_me: Boolean = false,
  preferences: Preferences = Preferences(),
  updated_at: DateTime = DateTime.now
)(implicit val _internalGroups: InternalGroups) extends HasUUID with TimeBased {

  lazy val internal_groups: Set[UUID] =
    _internalGroups.map(internal_groups_code)

  def groups: Set[UUID] = external_groups union internal_groups

  def hasPassword(submittedPasswd: Password) = {
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

  def updatePassword(newPassword: Password)(
    implicit _users: Users
  ): Future[User] = {
    _users.updatePassword(this, newPassword)
  }

  def save(implicit _users: Users) = _users.save(this)

  def addGroup(grp: InternalGroupsCode)(
    implicit _users: Users
  ): Future[User] = {
    val igc = internal_groups_code + grp
    _users
      .updateGroup(id, igc)
      .map(_ => copy(internal_groups_code = igc))
  }

  def delGroup(grp: InternalGroupsCode)(
    implicit _users: Users
  ): Future[User] = {
    val igc = internal_groups_code - grp
    _users
      .updateGroup(id, igc)
      .map(_ => copy(internal_groups_code = igc))
  }

  private def encrypt(salt: String, passwd: Password) =
    Crypto.sha2(s"$salt--$passwd")

  private def makeSalt(passwd: Password) =
    Crypto.sha2(s"${DateTime.now}--$passwd")
}

trait UserCanonicalNamed extends CanonicalNamed {

  override val basicName = "users"
}

/**
 *
 */
sealed abstract class UserTable
  extends NamedCassandraTable[UserTable, User]
  with UserCanonicalNamed {

  object id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object name
    extends StringColumn(this)

  object salt
    extends StringColumn(this)

  object encrypted_password
    extends StringColumn(this)

  object email
    extends StringColumn(this)

  object internal_groups
    extends IntColumn(this)

  object external_groups
    extends SetColumn[UserTable, User, UUID](this)

  object access_token
    extends StringColumn(this)

  object updated_at
    extends DateTimeColumn(this)

}

object User
  extends UserCanonicalNamed
  with ExceptionDefining {

  /**
   * Thrown when user is not found
   *
   * @param user email or uuid
   */
  case class NotFound(user: String)
    extends BaseException(error_code("not.found"))

  case class WrongPassword(email: String)
    extends BaseException(error_code("wrong.password"))

  case class SaltNotMatch(id: UUID)
    extends BaseException(error_code("salt.not.match"))

  case class AccessTokenNotMatch(id: UUID)
    extends BaseException(error_code("access_token.not.match"))

  case class AuthFailed()
    extends BaseException(error_code("auth.failed"))

  case class NoCredentials()
    extends BaseException(error_code("no.credentials"))

  case class EmailTaken(email: String)
    extends BaseException(error_code("email.taken"))

  implicit val jsonWrites = new Writes[User] {
    override def writes(o: User): JsValue = Json.obj(
      "id" -> o.id,
      "name" -> o.name,
      "email" -> o.email
    )
  }
}

class Users(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder,
  val _sysConfig: SysConfigs,
  val _internalGroups: InternalGroups
)
  extends UserTable
  with EntityTable[User]
  with ExtCQL[UserTable, User]
  with BasicPlayComponents
  with CassandraComponents
  with SysConfig
  with Logging {

  val _usersByEmail = new UsersByEmail

  create.ifNotExists.future()

  override def fromRow(r: Row): User = {
    User(
      id(r),
      Name(name(r)),
      salt(r),
      encrypted_password(r),
      EmailAddress(email(r)),
      InternalGroupsCode(internal_groups(r)),
      external_groups(r),
      Password.empty,
      remember_me = false,
      preferences = Preferences(),
      updated_at(r)
    )
  }

  lazy val root: Future[User] = System.UUID("root_id").map { uid =>
    User(id = uid, name = Name("root"))
  }

  def exists(id: UUID): Future[Boolean] = CQL {
    select(_.id).where(_.id eqs id)
  }.one.map {
    case None => throw User.NotFound(id.toString)
    case _    => true
  }

  def find(id: UUID): Future[User] = CQL {
    select.where(_.id eqs id)
  }.one().map {
    case None    => throw User.NotFound(id.toString)
    case Some(u) => u
  }

  def find(email: EmailAddress): Future[User] = {
    _usersByEmail.find(email).flatMap { case (_, id) => find(id) }
  }

  def find(ids: TraversableOnce[UUID]): Future[Seq[User]] = CQL {
    select
      .where(_.id in ids.toList.distinct)
  }.fetch()

  def checkEmail(email: EmailAddress): Future[Boolean] = {
    _usersByEmail.cql_check(email).one().map {
      case None => true
      case _    => throw User.EmailTaken(email.self)
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
      done <- _usersByEmail.save(u.email, u.id)
      user <- if (done) CQL {
        insert
          .value(_.id, u.id)
          .value(_.name, u.name.self)
          .value(_.salt, u.salt)
          .value(_.encrypted_password, u.encrypted_password)
          .value(_.email, u.email.self)
          .value(_.internal_groups, u.internal_groups_code.code | InternalGroupsCode.AnyoneMask)
          .value(_.external_groups, Set[UUID]())
          .value(_.updated_at, u.updated_at)
      }.future().map(_ => u)
      else throw User.EmailTaken(u.email.self)
    } yield user
  }

  def updateGroup(
    id: UUID,
    igc: InternalGroupsCode
  ): Future[ResultSet] = {
    CQL {
      update
        .where(_.id eqs id)
        .modify(_.internal_groups setTo igc.code)
    }.future()
  }

  def updatePassword(user: User, newPassword: Password): Future[User] = {
    val u = user.copy(password = newPassword).encryptPassword
    CQL {
      update
        .where(_.id eqs u.id)
        .modify(_.salt setTo u.salt)
        .and(_.encrypted_password setTo u.encrypted_password)
        .and(_.updated_at setTo DateTime.now)
    }.future().map(_ => u)
  }

  def updateName(id: UUID, name: String): Future[ResultSet] = CQL {
    update
      .where(_.id eqs id)
      .modify(_.name setTo name)
      .and(_.updated_at setTo DateTime.now)
  }.future()

  def remove(id: UUID): Future[ResultSet] = {
    find(id).flatMap { user =>
      CQL {
        Batch.logged
          .add(_usersByEmail.cql_del(user.email))
          .add(delete.where(_.id eqs id))
      }.future()
    }
  }

  def list(pager: Pager): Future[Page[User]] = {
    CQL(select).fetchEnumerator |>>>
      PIteratee.slice[User](pager.start, pager.limit)
  }.map(_.toIterable).map(Page(pager, _))

  def all: Enumerator[User] = {
    CQL(select).fetchEnumerator
  }

  def isEmpty: Future[Boolean] = CQL(select).one.map(_.isEmpty)

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

  def cql_updated_at(id: UUID) = CQL {
    update
      .where(_.id eqs id)
      .modify(_.updated_at setTo DateTime.now)
  }

  override def sortable: Set[SortableField] = Set(name, email)

  ////////////////////////////////////////////////////////////////
  def findAccessToken(id: UUID): Future[Option[String]] = {
    CQL(select(_.access_token).where(_.id eqs id)).one()
  }

  def auth(email: EmailAddress, passwd: Password): Future[User] = {
    find(email).map { user =>
      if (user.hasPassword(passwd)) user
      else throw User.WrongPassword(email.self)
    }
  }
}

sealed class UsersByEmailIndex
  extends CassandraTable[UsersByEmailIndex, (String, UUID)] {

  override val tableName = "users_email_index"

  object email
    extends StringColumn(this)
    with PartitionKey[String]

  object id extends UUIDColumn(this)

  override def fromRow(r: Row): (String, UUID) = (email(r), id(r))
}

class UsersByEmail(
  implicit
  val basicPlayApi: BasicPlayApi,
  val contactPoint: KeySpaceBuilder
)
  extends UsersByEmailIndex
  with ExtCQL[UsersByEmailIndex, (String, UUID)]
  with BasicPlayComponents
  with CassandraComponents
  with Logging {

  create.ifNotExists.future()

  def save(email: EmailAddress, id: UUID): Future[Boolean] = CQL {
    insert
      .value(_.email, email.self)
      .value(_.id, id)
      .ifNotExists()
  }.future().map(_.wasApplied())

  def find(email: EmailAddress): Future[(String, UUID)] = {
    if (email.self.isEmpty) Future.failed(User.NotFound(email.self))
    else CQL {
      select.where(_.email eqs email.self)
    }.one().map {
      case None      => throw User.NotFound(email.self)
      case Some(idx) => idx
    }
  }

  def cql_del(email: EmailAddress) = CQL {
    delete.where(_.email eqs email.self)
  }

  def cql_check(email: EmailAddress) = CQL {
    select(_.id).where(_.email eqs email.self)
  }

  def remove(email: EmailAddress): Future[ResultSet] = {
    cql_del(email).future()
  }
}

trait UsersComponents {

  def _groups: Groups

  implicit def _users: Users = _groups._users
}

object UsersComponents {

  implicit def _users(implicit _groups: Groups): Users = _groups._users
}