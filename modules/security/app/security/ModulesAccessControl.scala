package security

import java.util.UUID

import helpers.ExtString._
import helpers._
import models._

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
case class ModulesAccessControl(
  principal: User,
  access: ModulesAccessControl.Access,
  resource: ModulesAccessControl.CheckedModule
)(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _accessControls: AccessControls
) extends AccessControl[User, ModulesAccessControl.Access, ModulesAccessControl.CheckedModule]
  with BasicPlayComponents
  with DefaultPlayExecutor
  with I18nLoggingComponents {

  import security.ModulesAccessControl._

  override def canAccess: Boolean = false

  override def canAccessAsync: Future[Boolean] = {
    Future.failed(Denied(s"User(${principal.id.toString}, ${principal.email})", access, resource))
      .fallbackTo(checkGroups(resource, access, principal.groups))
      .fallbackTo(checkUser(resource, access, principal.id))
      .andThen {
        case Failure(e: Denied)        => Logger.debug(s"Permission check failed, because ${e.reason}")
        case Failure(e: BaseException) => Logger.error(s"Permission check failed, because ${e.reason}", e)
        case Failure(e: Throwable)     => Logger.error(s"Permission check failed.", e)
      }
  }

  def checkGroups(
    resource: CheckedModule,
    access: Access,
    group_ids: Traversable[UUID]
  ): Future[Boolean] = {
    val gids = group_ids.toSet
    _accessControls
      .findPermissions(resource.name, gids)
      .map(_.map(Permission.apply))
      .map { permissions =>
        Logger.trace(
          s"""
          |Groups      : $group_ids
          |Module      : $resource
          |Access      : $access
          |${permissions.map(p => s"Permissions : $p").mkString("\n")}
         """.stripMargin
        )

        if (Permission.union(permissions: _*) ? access) true
        else throw Denied(group_ids.toString(), access, resource)
      }
  }

  def checkUser(
    resource: CheckedModule,
    access: Access,
    user_id: UUID
  ): Future[Boolean] =
    _accessControls
      .findPermission(resource.name, user_id)
      .map(_.map(Permission.apply).getOrElse(Permission.Nothing))
      .map { permission =>
        Logger.trace(
          s"""
          |User       : $user_id
          |Module     : $resource
          |Access     : $access
          |Permission : $permission
         """.stripMargin
        )

        if (permission ? access) true
        else throw Denied(user_id.toString, access, resource)
      }

}

object ModulesAccessControl
  extends CanonicalNamed
    with ExceptionDefining {

  override def basicName: String = "modules"

  case class Denied(
    principal: String,
    access: Access,
    resource: CheckedModule
  ) extends AccessControl.Denied[String, Access, CheckedModule](canonicalName)

  case class Access(self: Long) extends AnyVal {

    def |(that: => Access) = Access(self | that.self)

    def toPermission = Permission(self)

    override def toString = BinaryString.from(self).toString
  }

  object Access {

    case class Pos(self: Int) extends AnyVal {

      def toAccess = Access(1L << self.min(63).max(0))

      override def toString = self.toString
    }

    object Pos {

      implicit def PosToAccess(pos: Pos): Access = pos.toAccess
    }

    val Nothing = Access(0L)

    def union(accesses: Iterable[Access]) = (Nothing /: accesses) (_ | _)
  }

  case class Permission(self: Long) extends AnyVal {

    def |(that: => Permission) = Permission(self | that.self)

    def ?(access: => Access) = (self & access.self) == access.self

    override def toString = BinaryString.from(self).toString
  }

  object Permission {

    val Anything = Permission(0xFFFFFFFFFFFFFFFFL)
    val Nothing  = Permission(0x0000000000000000L)

    def union(permissions: Permission*) = (Nothing /: permissions) (_ | _)
  }

  case class CheckedModule(name: String) extends AnyVal {

    override def toString = name
  }

  trait BasicAccessDef {

    def Pos(pos: Int) = Access(1L << pos.min(63).max(0))

    /** Access : New */
    val P00 = Access.Pos(0)
    /** Access : Create */
    val P01 = Access.Pos(1)
    /** Access : Show */
    val P02 = Access.Pos(2)
    /** Access : Index */
    val P03 = Access.Pos(3)
    /** Access : Edit */
    val P04 = Access.Pos(4)
    /** Access : Save */
    val P05 = Access.Pos(5)
    /** Access : Destroy */
    val P06 = Access.Pos(6)
    /** Access : Index History */
    val P07 = Access.Pos(7)
    // 8 ~ 15 are preserved

    def values: Seq[Access.Pos]

    def permission: Permission = Access.union(values.map(_.toAccess)).toPermission
  }
}