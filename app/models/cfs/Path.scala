package models.cfs

import play.api.mvc.PathBindable
import play.utils.UriEncoding

/**
 * @author zepeng.li@gmail.com
 */
case class Path(dirs: Seq[String] = Seq(), filename: Option[String] = None) {

  override def toString = ("" /: dirs)(_ + _ + "/") + filename.getOrElse("")

  def /(dir: String) = copy(dirs ++ Seq(dir))

  def +(filename: String) = copy(filename = Some(filename))
}

object Path {
  def apply(filename: String): Path = Path(filename = Some(filename))

  implicit def bindablePath: PathBindable[Path] = new PathBindable[Path] {

    def bind(key: String, value: String): Either[String, Path] = {
      val path1 = """(.+)/([^/]+)$""".r
      val path2 = """(.+)/$""".r
      val path3 = """(.+)$""".r
      val path = value match {
        case path1(dirs, f) => Path(decodeDirs(dirs), Some(decode(f)))
        case path2(dirs)    => Path(decodeDirs(dirs), None)
        case path3(f)       => Path(Seq(), Some(decode(f)))
        case _              => Path(Seq(), None)
      }
      Right(path)
    }

    def unbind(key: String, path: Path): String = {
      encodeDirs(path.dirs) + encode(path.filename.getOrElse(""))
    }
  }

  def decodeDirs(dirs: String): Array[String] = {
    dirs.split("/").filter(_.nonEmpty).map(decode)
  }

  def encodeDirs(dirs: Seq[String]): String = {
    ("" /: dirs.map(encode))(_ + _ + "/")
  }

  def decode: (String) => String = {
    UriEncoding.decodePathSegment(_, charset)
  }

  def encode: (String) => String = {
    UriEncoding.encodePathSegment(_, charset)
  }

  val charset = "utf-8"
}