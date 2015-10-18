package models.cfs

import play.api.libs.json._

/**
 * @author zepeng.li@gmail.com
 */
object FileSystem {

  case class Permission(self: Long) extends AnyVal {

    def |(that: Permission) = Permission(self | that.self)

    def ?(role: Roles => Role, access: Access) =
      (self & role(Role).permit(access).self) > 0

    override def toString = pprint

    def pprint = {
      "%63s".format(self.toBinaryString)
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
  }

  object Permission {

    implicit val jsonFormat = Format(
      Reads.LongReads.map(Permission.apply),
      Writes[Permission](o => JsNumber(o.self))
    )
  }

  case class Role(pos: Long) extends AnyVal {

    def r = permit(Access.r)
    def w = permit(Access.w)
    def x = permit(Access.x)
    def rw = permit(Access.rw)
    def rx = permit(Access.rx)
    def wx = permit(Access.wx)
    def rwx = permit(Access.rwx)

    def permit(access: Access) = Permission(access.self.toLong << pos)
  }

  object Role extends Roles

  trait Roles {

    val owner = Role(20 * 3)
    val other = Role(0)

    def group(gid: Int) = if (gid < 0 || gid > 18) other else Role((19 - gid) * 3)
  }

  case class Access(self: Int = 0) extends AnyVal {

    def |(that: Access) = Access(self | that.self)

    def ?(access: Access) = (self & access.self) > 0

    override def toString = pprint

    def pprint = {
      val r = if ((self & 4) > 0) 'r' else '-'
      val w = if ((self & 2) > 0) 'w' else '-'
      val x = if ((self & 1) > 0) 'x' else '-'
      s"$r$w$x"
    }
  }

  object Access {

    val r   = Access(4)
    val w   = Access(2)
    val x   = Access(1)
    val wx  = Access(2 | 1)
    val rx  = Access(4 | 1)
    val rw  = Access(4 | 2)
    val rwx = Access(4 | 2 | 1)

    implicit val jsonFormat = Format(
      Reads.IntReads.map(Access.apply),
      Writes[Access](o => JsNumber(o.self))
    )
  }
}