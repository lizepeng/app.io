package models.cfs

import play.api.mvc.PathBindable

/**
 * @author zepeng.li@gmail.com
 */
case class Path(dirs: Seq[String], filename: Option[String]) {
  override def toString = ("" /: dirs)(_ + _ + "/") + filename.getOrElse("")
}

object Path {
  implicit def bindablePath() = new PathBindable[Path] {

    def bind(key: String, value: String): Either[String, Path] = {
      val path1 = """(.+)/([^/]+)$""".r
      val path2 = """(.+)/$""".r
      val path3 = """(.+)$""".r
      value match {
        case path1(p, f) => Right(Path(p.split("/").filter(_.nonEmpty), Some(f)))
        case path2(p)    => Right(Path(p.split("/").filter(_.nonEmpty), None))
        case path3(f)    => Right(Path(Seq(), Some(f)))
        case _           => Right(Path(Seq(), None))
      }
    }

    def unbind(key: String, path: Path): String = path.toString
  }
}