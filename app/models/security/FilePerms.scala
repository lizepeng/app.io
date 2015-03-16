package models.security

import controllers.UserRequest
import helpers.syntax.PolarQuestion
import models.User
import models.cfs.INode
import play.api.mvc.AnyContent

/**
 * @author zepeng.li@gmail.com
 */
case class FilePerms(
  principal: User,
  action: Int,
  resource: INode
) extends Permission[User, Int, INode] {

  override def canAccess: Boolean = {
    //check owner permission
    if (principal.id == resource.owner_id) {
      val mask = action.toLong << 20 * 3
      if ((resource.permission & mask) == mask) return true
    }
    //check internal group permission
    for (gid <- principal.internal_groups) {
      val mask = action.toLong << (19 - gid) * 3
      if ((resource.permission & mask) == mask) return true
    }
    //check other permission
    (resource.permission & action) == action
  }

  private def pprintLine(length: Int) = (
    for (i <- 0 to length) yield {"---"}
    ).mkString("+", "+", "+")

  private def pprintIndices(length: Int) = (
    for (i <- 0 to length) yield {"%3d".format(i)}
    ).mkString("|", "|", "|")

  private def pprintPerms(perm: Long) = {
    "%63s".format(perm.toBinaryString)
      .grouped(3).map(toRWX).mkString("|", "|", "|")
  }

  private def toRWX(code: String): String = {
    if (code.length != 3) return "???"

    def mapAt(i: Int, readable: String) = {
      val c = code.charAt(i)
      if (c == ' ' || c == '0') "-"
      else readable
    }
    s"${mapAt(0, "r")}${mapAt(1, "w")}${mapAt(2, "x")}"
  }

  override def toString =
    s"""
      user_id  : ${principal.id}
      action   : ${toRWX(action.toBinaryString)}
      inode_id : ${resource.id}
      ${pprintLine(20)}
      ${pprintIndices(20)}
      ${pprintPerms(resource.permission)}
      ${principal.internal_groups.pprintLine1}
      ${principal.internal_groups.pprintLine2}
      ${pprintLine(20)}
     """

}

object FilePerms {

  case class Denied(perm: FilePerms) extends Permission.Denied("cfs")

  val r   = 4
  val w   = 2
  val x   = 1
  val rw  = r | w
  val rx  = r | x
  val wx  = w | x
  val rwx = r | w | x

  def apply(inode: INode)(
    implicit request: UserRequest[AnyContent]
  ) = FilePermsBuilder(inode, request)

  case class FilePermsBuilder(
    inode: INode, request: UserRequest[AnyContent]
  ) {
    val r   = new PolarQuestion {def ? = check(FilePerms.r)}
    val w   = new PolarQuestion {def ? = check(FilePerms.w)}
    val x   = new PolarQuestion {def ? = check(FilePerms.x)}
    val rw  = new PolarQuestion {def ? = check(FilePerms.rw)}
    val rx  = new PolarQuestion {def ? = check(FilePerms.rx)}
    val wx  = new PolarQuestion {def ? = check(FilePerms.wx)}
    val rwx = new PolarQuestion {def ? = check(FilePerms.rwx)}

    private def check(action: Int): Boolean = {
      val perm: FilePerms = build(action)
      if (perm.canAccess) true
      else throw Denied(perm)
    }

    private def build(action: Int): FilePerms = {
      request.user match {
        case None    => throw User.IsNotLoggedIn()
        case Some(u) => FilePerms(u, action, inode)
      }
    }
  }

}