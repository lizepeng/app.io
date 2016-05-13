package security

import java.util.UUID

import helpers._
import models._
import models.cfs.CassandraFileSystem._
import models.cfs._

/**
 * @author zepeng.li@gmail.com
 */
case class FileSystemAccessControl(
  principal: User,
  access: Access,
  resource: INode
)(
  implicit val basicPlayApi: BasicPlayApi
) extends AccessControl[User, Access, INode]
  with BasicPlayComponents
  with I18nLoggingComponents {

  import FileSystemAccessControl._

  override def canAccess: Boolean = {
    try {
      Logger.trace(this.pprint)
      canAccess0
    } catch {
      case e: Denied        => Logger.debug(e.reason)
        throw e
      case e: BaseException => Logger.error(e.reason, e)
        throw Denied(principal.id, access, resource.path)
      case e: Throwable     => Logger.error(s"can not access file.", e)
        throw Denied(principal.id, access, resource.path)
    }
  }

  private def canAccess0: Boolean = {
    //check owner permission
    if (principal.id == resource.owner_id) {
      if (resource.permission ?(_.owner, access)) return true
    }
    //check external group permission
    for (gid <- principal.external_groups if resource.ext_permission.contains(gid)) {
      if (resource.ext_permission(gid) ?(_.other, access)) return true
    }
    //check internal group permission
    for (gid <- principal.internal_group_bits.toInternalGroups) {
      if (resource.permission ?(_.group(gid), access)) return true
    }
    //check other permission
    if (resource.permission ?(_.other, access)) true
    else throw Denied(principal.id, access, resource.path)
  }

  private def pprintLine(length: Int) =
    (for (i <- 0 to length) yield "---").mkString("+", "+", "+")

  private def pprintIndices(length: Int) =
    (for (i <- 0 to length) yield f"$i%3d").mkString("|", "|", "|")

  override def toString = pprint

  def pprint =
    s"""
      |${pprintLine(20)}
      ||  path     : ${resource.path}
      ||  user_id  : ${principal.id}
      ||  access   : ${access.pprint}
      |${pprintLine(20)}
      |${pprintIndices(20)}
      |${resource.permission.pprint}
      |${principal.internal_group_bits.pprintLine1}
      |${principal.internal_group_bits.pprintLine2}
      |${pprintLine(20)}
     """.stripMargin
}

object FileSystemAccessControl
  extends CanonicalNamed
    with ExceptionDefining {

  override def basicName = CassandraFileSystem.basicName

  case class Denied(
    principal: UUID,
    access: Access,
    resource: Path
  ) extends AccessControl.Denied[UUID, Access, Path](canonicalName)

  implicit class INodePermissionChecker(val inode: INode) extends AnyVal {

    import helpers.syntax.{PolarQuestion => PQ}
    import helpers.{BasicPlayApi => BPA}

    def r(implicit u: User, bpa: BPA) = new PQ {def ? = check(Access.r)}
    def w(implicit u: User, bpa: BPA) = new PQ {def ? = check(Access.w)}
    def x(implicit u: User, bpa: BPA) = new PQ {def ? = check(Access.x)}
    def rw(implicit u: User, bpa: BPA) = new PQ {def ? = check(Access.rw)}
    def rx(implicit u: User, bpa: BPA) = new PQ {def ? = check(Access.rx)}
    def wx(implicit u: User, bpa: BPA) = new PQ {def ? = check(Access.wx)}
    def rwx(implicit u: User, bpa: BPA) = new PQ {def ? = check(Access.rwx)}

    private def check(access: Access)(
      implicit user: User, basicPlayApi: BasicPlayApi
    ): Boolean = FileSystemAccessControl(user, access, inode).canAccess
  }
}