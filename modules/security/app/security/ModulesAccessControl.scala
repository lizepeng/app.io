package security

import java.util.UUID

import helpers._
import models._

import scala.concurrent.Future
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
)
  extends AccessControl[User, ModulesAccessControl.Access, ModulesAccessControl.CheckedModule]
  with BasicPlayComponents
  with DefaultPlayExecutor
  with Logging {

  import security.ModulesAccessControl._

  override def canAccess: Boolean = false

  override def canAccessAsync: Future[Boolean] = {

    checkGroups(resource, access, principal.groups)
      .andThen {
      case Failure(e: Denied) => Logger.debug("Groups " + e.reason)
    }.recoverWith {
      case e: Denied => checkUser(resource, access, principal.id)
    }.andThen {
      case Failure(e: Denied)    => Logger.debug("User   " + e.reason)
      case Failure(e: Throwable) => Logger.error(e.getMessage)
    }.recover {
      case e: Throwable if !e.isInstanceOf[Denied] =>
        throw Denied(principal.id.toString, access, resource)
    }.andThen {
      case Failure(r: Denied) => Logger.debug(r.reason)
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
          |Access      :
          |$access
          |Permissions :
          |${permissions.mkString("\n")}
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
          |Access     :
          |$access
          |Permission :
          |$permission
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

    override def toString = f"${self.toBinaryString}%64s".replace(' ', '0')
  }

  case class Permission(self: Long) extends AnyVal {

    def |(that: => Permission) = Permission(self | that.self)

    def ?(access: => Access) = (self & access.self) == access.self

    override def toString = f"${self.toBinaryString}%64s".replace(' ', '0')
  }

  object Permission {

    val Anything = Permission(0xffffffffffffffffL)
    val Nothing  = Permission(0x0000000000000000L)

    def union(permissions: Permission*) = (Nothing /: permissions)(_ | _)
  }

  case class CheckedModule(name: String) extends AnyVal {

    override def toString = name
  }

  trait AccessDefinition {

    val Anything     = Access(0xffffffffffffffffL)
    val Nothing      = Access(0x0000000000000000L)

    val NNew         = Access(0x0000000000000001L)
    val Create       = Access(0x0000000000000002L)
    val Show         = Access(0x0000000000000004L)
    val Index        = Access(0x0000000000000008L)
    val Edit         = Access(0x0000000000000010L)
    val Save         = Access(0x0000000000000020L)
    val Destroy      = Access(0x0000000000000040L)
    val HistoryIndex = Access(0x0000000000000080L)
    val AddRelation  = Access(0x0000000000000100L)
    val DelRelation  = Access(0x0000000000000200L)

    def union(as: Access*) = (Nothing /: as)(_ | _)

    val ALL = Seq(
      NNew,
      Create,
      Show,
      Index,
      Edit,
      Save,
      Destroy,
      HistoryIndex,
      AddRelation,
      DelRelation
    )
  }

  object AccessDefinition extends AccessDefinition

}