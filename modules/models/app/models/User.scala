package models

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs
import com.datastax.driver.core.{ResultSet, Row}
import com.websudos.phantom.dsl._
import com.websudos.phantom.iteratee.{Iteratee => PIteratee}
import helpers._
import models.cassandra._
import models.sys.{SysConfig, SysConfigs}
import org.joda.time.DateTime
import play.api.libs.Crypto
import play.api.libs.functional.syntax._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.collection.TraversableOnce
import scala.concurrent.Future
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
  remember_me: Boolean = false,
  updated_at: DateTime = DateTime.now
)(implicit val internalGroupsRepo: InternalGroupsMapping) extends HasUUID {

  def groups: Set[UUID] =
    ext_groups union internalGroupsRepo.toGroupIdSet(int_groups)

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

  def savePassword(newPassword: String)(
    implicit User: Users
  ): Future[User] = {
    User.savePassword(this, newPassword)
  }

  def save(implicit User: Users) = User.save(this)

  private def encrypt(salt: String, passwd: String) =
    Crypto.sha2(s"$salt--$passwd")

  private def makeSalt(passwd: String) =
    Crypto.sha2(s"${DateTime.now}--$passwd")
}

/**
 *
 */
sealed class UserTable
  extends CassandraTable[UserTable, User]

  with CanonicalNamedModel[User]
  with CanonicalModel[User]
  with Logging {

  override val tableName = "users"

  object id
    extends UUIDColumn(this)
    with PartitionKey[UUID]
    with JsonReadable[UUID] {

    def reads = (__ \ "id").read[UUID]
  }

  object name
    extends StringColumn(this)
    with JsonReadable[String] {

    def reads = (__ \ "name").read[String](
      minLength[String](2) keepAnd maxLength[String](255)
    )
  }

  object salt
    extends StringColumn(this)

  object encrypted_password
    extends StringColumn(this)

  object email
    extends StringColumn(this)
    with JsonReadable[String] {

    def reads = (__ \ "email").read[String](Reads.email)
  }

  object internal_groups
    extends IntColumn(this)
    with JsonReadable[Int] {

    def reads = (__ \ "int_groups").read[Int]
  }

  object external_groups
    extends SetColumn[UserTable, User, UUID](this)
    with JsonReadable[Set[UUID]] {

    def reads = (__ \ "ext_groups").read[Set[UUID]]
  }

  object updated_at
    extends DateTimeColumn(this)
    with JsonReadable[DateTime] {

    def reads = always(DateTime.now)
  }

  //TODO
  override def fromRow(r: Row): User = ???
}

object User
  extends UserTable
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

  case class AuthFailed()
    extends BaseException(error_code("auth.failed"))

  case class NoCredentials()
    extends BaseException(error_code("no.credentials"))

  case class EmailTaken(email: String)
    extends BaseException(error_code("email.taken"))

  object AccessControl {

    import models.{AccessControl => AC}

    case class Undefined(
      principal: UUID,
      action: String,
      resource: String
    ) extends AC.Undefined[UUID](action, resource, basicName)

    case class Denied(
      principal: UUID,
      action: String,
      resource: String
    ) extends AC.Denied[UUID](action, resource, basicName)

    case class Granted(
      principal: UUID,
      action: String,
      resource: String
    ) extends AC.Granted[UUID](action, resource, basicName)

  }

  case class Credentials(id: UUID, salt: String)

}

class Users(
  implicit
  val sysConfig: SysConfigs,
  val internalGroupsRepo: InternalGroupsMapping,
  val basicPlayApi: BasicPlayApi
)
  extends UserTable
  with ExtCQL[UserTable, User]
  with BasicPlayComponents
  with Cassandra
  with SysConfig {

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
      remember_me = false,
      updated_at(r)
    )
  }

  import User._

  val UserByEmail = new UserByEmail

  lazy val root: Future[User] = System.UUID("root_id").map { uid =>
    User(id = uid, name = "root")
  }

  def exists(id: UUID): Future[Boolean] = CQL {
    select(_.id).where(_.id eqs id)
  }.one.map {
    case None => throw NotFound(id.toString)
    case _    => true
  }

  def find(id: UUID): Future[User] = CQL {
    select.where(_.id eqs id)
  }.one().map {
    case None    => throw NotFound(id.toString)
    case Some(u) => u
  }

  def find(email: String): Future[User] = {
    UserByEmail.find(email).flatMap { case (_, id) => find(id) }
  }

  def find(ids: TraversableOnce[UUID]): Future[Seq[User]] = CQL {
    select
      .where(_.id in ids.toList.distinct)
  }.fetch()

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
          .value(_.updated_at, u.updated_at)
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
        .and(_.updated_at setTo DateTime.now)
    }.future().map(_ => u)
  }

  def saveName(id: UUID, name: String): Future[ResultSet] = CQL {
    update
      .where(_.id eqs id)
      .modify(_.name setTo name)
      .and(_.updated_at setTo DateTime.now)
  }.future()

  def remove(id: UUID): Future[ResultSet] = {
    find(id).flatMap { user =>
      CQL {
        Batch.logged
          .add(UserByEmail.cql_del(user.email))
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

  ////////////////////////////////////////////////////////////////

  /**
   *
   * @param cred credentials
   * @return
   */
  def auth(cred: Credentials): Future[User] = {
    find(cred.id).map { u =>
      if (u.salt == cred.salt) u
      else throw SaltNotMatch(cred.id)
    }
  }

  def auth(email: String, passwd: String): Future[User] = {
    find(email).map { user =>
      if (user.hasPassword(passwd)) user
      else throw WrongPassword(email)
    }
  }
}

//TODO rename to *Table
sealed class UserByEmailIndex
  extends CassandraTable[UserByEmailIndex, (String, UUID)]

  with Logging {

  override val tableName = "users_email_index"

  object email
    extends StringColumn(this)
    with PartitionKey[String]

  object id extends UUIDColumn(this)

  override def fromRow(r: Row): (String, UUID) = (email(r), id(r))
}

object UserByEmailIndex extends UserByEmailIndex

class UserByEmail(
  implicit val basicPlayApi: BasicPlayApi
)
  extends UserByEmailIndex
  with ExtCQL[UserByEmailIndex, (String, UUID)]
  with BasicPlayComponents
  with Cassandra {

  def save(email: String, id: UUID): Future[Boolean] = CQL {
    insert
      .value(_.email, email)
      .value(_.id, id)
      .ifNotExists()
  }.future().map(_.wasApplied())

  def find(email: String): Future[(String, UUID)] = {
    if (email.isEmpty) Future.failed(User.NotFound(email))
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