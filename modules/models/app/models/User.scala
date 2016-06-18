package models

import java.security.SecureRandom
import java.util.UUID

import com.websudos.phantom.dsl._
import helpers.ExtCodecs._
import helpers._
import models.cassandra._
import models.misc._
import models.sys._
import org.joda.time.DateTime
import org.mindrot.jbcrypt.BCrypt
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._

import scala.collection.TraversableOnce
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
case class User(
  id: UUID,
  user_name: UserName = UserName.default,
  salt: String = "",
  encrypted_password: String = "",
  session_id: Option[String] = None,
  email: EmailAddress = EmailAddress.empty,
  internal_group_bits: InternalGroupBits = InternalGroup.Anyone,
  internal_groups: Set[UUID] = Set(),
  external_groups: Set[UUID] = Set(),
  password: Password = Password.empty,
  remember_me: Boolean = false,
  attributes: Attributes = Attributes(),
  updated_at: DateTime = DateTime.now
) extends HasUUID with TimeBased {

  object preferences extends JsonOptionalStringifier.FormatAsImplicit {

    def apply[T](key: String)(
      implicit _users: Users, fmt: Format[T]
    ) = _users.preferences.find(id, key)

    def +[T](entry: (String, T))(
      implicit _users: Users, fmt: Format[T]
    ) = _users.preferences.save(id, entry)

    def -(key: String)(
      implicit _users: Users
    ) = _users.preferences.remove(id, key)
  }

  def groups: Set[UUID] = external_groups union internal_groups

  def hasPassword(passwd: Password) = {
    if (encrypted_password == "") false
    else BCrypt.checkpw(s"$salt--${passwd.self}", encrypted_password)
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

  def addGroup(grp: InternalGroup)(
    implicit _users: Users
  ): Future[User] = _users.updateGroup(copy(internal_group_bits = internal_group_bits + grp))

  def delGroup(grp: InternalGroup)(
    implicit _users: Users
  ): Future[User] = _users.updateGroup(copy(internal_group_bits = internal_group_bits - grp))

  def withNewSessionId = copy(session_id = Some(makeSessionId))

  def withNoSessionId = copy(session_id = None)

  private def encrypt(salt: String, passwd: Password) =
    BCrypt.hashpw(s"$salt--${passwd.self}", BCrypt.gensalt())

  private def makeSalt(passwd: Password) =
    Codecs.sha2(randomString, length = 256)

  private def makeSessionId =
    Codecs.sha2(randomString, length = 256)

  private def randomString = BigInt(130, User.secureRandom).toString(32)
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
    extends TimeUUIDColumn(this)
      with PartitionKey[UUID]

  object user_name
    extends StringColumn(this)

  object salt
    extends StringColumn(this)

  object encrypted_password
    extends StringColumn(this)

  object session_id
    extends OptionalStringColumn(this)

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

  val secureRandom = SecureRandom.getInstance("NativePRNGNonBlocking")

  /**
   * Thrown when user is not found
   *
   * @param user email or uuid
   */
  case class NotFound(user: String)
    extends BaseException(error_code("not.found"))

  case class NoCredentials()
    extends BaseException(error_code("no.credentials"))

  case class SessionIdNotMatch(id: UUID)
    extends BaseException(error_code("session_id.not.match"))

  case class AccessTokenNotMatch(id: UUID)
    extends BaseException(error_code("access_token.not.match"))

  case class WrongPassword(email: String)
    extends BaseException(error_code("wrong.password"))

  case class AuthFailed()
    extends BaseException(error_code("auth.failed"))

  case class EmailTaken(email: String)
    extends BaseException(error_code("email.taken"))

  implicit val jsonWrites = new Writes[User] {
    override def writes(o: User): JsValue = Json.obj(
      "id" -> o.id,
      "user_name" -> o.user_name,
      "email" -> o.email
    )
  }
}

class Users(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef,
  val _sysConfig: SysConfigs,
  val _internalGroups: InternalGroups
) extends UserTable
  with EntityTable[User]
  with ExtCQL[UserTable, User]
  with BasicPlayComponents
  with CassandraComponents
  with AppDomainComponents
  with SystemAccounts
  with BootingProcess
  with Logging {

  val byEmail     = new UsersByEmail
  val preferences = new UserPreferences

  onStart(CQL(create.ifNotExists).future())

  override def fromRow(r: Row): User = {
    val ig_bits = InternalGroupBits(internal_groups(r))
    User(
      id(r),
      UserName(user_name(r)),
      salt(r),
      encrypted_password(r),
      session_id(r),
      EmailAddress(email(r)),
      ig_bits,
      _internalGroups.find(ig_bits),
      external_groups(r),
      Password.empty,
      remember_me = false,
      attributes = Attributes(),
      updated_at(r)
    )
  }

  def root = _systemAccount(User)(context, this)

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
    byEmail.find(email).flatMap { case (_, id) => find(id) }
  }

  def find(ids: TraversableOnce[UUID]): Future[Seq[User]] = CQL {
    select
      .where(_.id in ids.toList.distinct)
  }.fetch()

  def checkEmail(email: EmailAddress): Future[Boolean] = {
    byEmail.cql_check(email).one().map {
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
      ____ <- byEmail.save(u.email, u.id)
      user <- CQL {
        insert
          .value(_.id, u.id)
          .value(_.user_name, u.user_name.self)
          .value(_.salt, u.salt)
          .value(_.encrypted_password, u.encrypted_password)
          .value(_.email, u.email.self)
          .value(_.internal_groups, (u.internal_group_bits + InternalGroup.Anyone).bits)
          .value(_.external_groups, Set[UUID]())
          .value(_.updated_at, u.updated_at)
      }.future().map(_ => u)
    } yield user
  }

  def saveSessionId(user: User, maxAge: FiniteDuration): Future[User] = CQL {
    update
      .where(_.id eqs user.id)
      .modify(_.session_id setTo user.session_id)
      .ttl(maxAge)
  }.future.map(_ => user)

  def updateGroup(user: User): Future[User] = CQL {
    update
      .where(_.id eqs user.id)
      .modify(_.internal_groups setTo (user.internal_group_bits + InternalGroup.Anyone).bits)
  }.future().map { _ =>
    user.copy(internal_groups = _internalGroups.find(user.internal_group_bits))
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

  def remove(id: UUID): Future[ResultSet] = {
    find(id).flatMap { user =>
      CQL {
        Batch.logged
          .add(byEmail.cql_del(user.email))
          .add(delete.where(_.id eqs id))
      }.future()
    }
  }

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

  override def sortable: Set[SortableField] = Set(user_name, email)

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
  extends NamedCassandraTable[UsersByEmailIndex, (String, UUID)]
    with UsersByEmailCanonicalNamed {

  object email
    extends StringColumn(this)
      with PartitionKey[String]

  object id extends UUIDColumn(this)

  override def fromRow(r: Row): (String, UUID) = (email(r), id(r))
}

trait UsersByEmailCanonicalNamed extends CanonicalNamed {

  override val basicName = "users_email_index"
}

class UsersByEmail(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
) extends UsersByEmailIndex
  with ExtCQL[UsersByEmailIndex, (String, UUID)]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(CQL(create.ifNotExists).future())

  def save(email: EmailAddress, id: UUID): Future[Boolean] = CQL {
    insert
      .value(_.email, email.self)
      .value(_.id, id)
      .ifNotExists()
  }.future().map { ret =>
    if (ret.wasApplied()) true
    else throw User.EmailTaken(email.self)
  }

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

sealed class UserPreferencesTable
  extends NamedCassandraTable[UserPreferencesTable, UUID]
    with UserPreferencesCanonicalNamed {

  object id
    extends UUIDColumn(this)
      with PartitionKey[UUID]

  object key
    extends StringColumn(this)
      with PrimaryKey[String]

  object value
    extends StringColumn(this)

  override def fromRow(r: Row) = id(r)
}

trait UserPreferencesCanonicalNamed extends CanonicalNamed {

  override val basicName = "user_preferences"
}

class UserPreferences(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
) extends UserPreferencesTable
  with ExtCQL[UserPreferencesTable, UUID]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(CQL(create.ifNotExists).future())

  def find[T](id: UUID, key: String)(
    implicit jos: JsonOptionalStringifier[T]
  ): Future[Option[T]] = CQL {
    select(_.value)
      .where(_.id eqs id)
      .and(_.key eqs key)
  }.one().map(_.flatMap(jos.optionalFromJson))

  def save[T](id: UUID, entry: (String, T))(
    implicit jos: JsonOptionalStringifier[T]
  ): Future[ResultSet] = CQL {
    val (key, value) = entry
    update.where(_.id eqs id)
      .and(_.key eqs key)
      .modify(_.value setTo jos.toJson(value))
      .ttl(365 days)
  }.future()

  def remove(id: UUID, key: String): Future[ResultSet] =
    CQL {
      delete
        .where(_.id eqs id)
        .and(_.key eqs key)
    }.future()
}


trait UsersComponents {

  def _groups: Groups

  implicit def _users: Users = _groups._users
}