package security

import java.util.UUID

import helpers.syntax.PolarQuestion
import models._
import models.cfs.FileSystem.Access
import models.cfs._

/**
 * @author zepeng.li@gmail.com
 */
case class FilePermission(
  principal: User,
  action: Access,
  resource: INode
) extends Permission[User, Access, INode] {

  override def canAccess: Boolean = {
    //check owner permission
    if (principal.id == resource.owner_id) {
      if (resource.permission ?(_.owner, action)) return true
    }
    //check external group permission
    for (gid <- principal.external_groups if resource.ext_permission.contains(gid)) {
      if (resource.ext_permission(gid) ? action) return true
    }
    //check internal group permission
    for (gid <- principal.internal_groups_code.numbers) {
      if (resource.permission ?(_.group(gid), action)) return true
    }
    //check other permission
    resource.permission ?(_.other, action)
  }

  private def pprintLine(length: Int) =
    for (i <- 0 to length) yield {"---"}.mkString("+", "+", "+")

  private def pprintIndices(length: Int) =
    for (i <- 0 to length) yield {"%3d".format(i)}.mkString("|", "|", "|")

  override def toString =
    s"""
      user_id  : ${principal.id}
      action   : ${action.pprint}
      inode_id : ${resource.id}
      ${pprintLine(20)}
      ${pprintIndices(20)}
      ${resource.permission.pprint}
      ${principal.internal_groups_code.pprintLine1}
      ${principal.internal_groups_code.pprintLine2}
      ${pprintLine(20)}
     """
}

object FilePermission {

  case class Denied(
    principal: UUID,
    action: Access,
    resource: INode
  ) extends Permission.Denied[UUID, Access, INode](CassandraFileSystem.canonicalName)

  implicit class INodePermissionChecker(val inode: INode) extends AnyVal {

    def r(implicit user: User) = new PolarQuestion {def ? = check(Access.r)}
    def w(implicit user: User) = new PolarQuestion {def ? = check(Access.w)}
    def x(implicit user: User) = new PolarQuestion {def ? = check(Access.x)}
    def rw(implicit user: User) = new PolarQuestion {def ? = check(Access.rw)}
    def rx(implicit user: User) = new PolarQuestion {def ? = check(Access.rx)}
    def wx(implicit user: User) = new PolarQuestion {def ? = check(Access.wx)}
    def rwx(implicit user: User) = new PolarQuestion {def ? = check(Access.rwx)}

    private def check(access: Access)(implicit user: User): Boolean = {
      if (FilePermission(user, access, inode).canAccess) true
      else throw Denied(user.id, access, inode)
    }
  }
}