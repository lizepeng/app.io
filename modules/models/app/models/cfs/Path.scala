package models.cfs

import helpers.JsonStringifier
import play.api.libs.json.{Format, Json}
import play.api.mvc.PathBindable
import play.utils.UriEncoding

/**
 * @author zepeng.li@gmail.com
 */
case class Path(parts: Seq[String] = Seq(), filename: Option[String] = None) {

  override def toString = ("" /: parts)(_ + _ + "/") + filename.getOrElse("")

  def /(dir: String) = if (dir == ".") this else copy(parts ++ Seq(dir))

  def /:(dir: String) = if (dir == ".") this else copy(Seq(dir) ++ parts)

  def +(filename: String) = copy(filename = Some(filename))

  def subPaths = {
    for (i <- 0 to parts.size)
      yield Path(parts.take(i), None)
  } ++ {
    if (filename.nonEmpty) Seq(this) else Seq.empty
  }
}

object Path extends PathJsonStringifier{
  
  def root = Path()

  implicit def bindablePath: PathBindable[Path] = new PathBindable[Path] {

    def bind(key: String, value: String): Either[String, Path] = {
      val path1 = """(.+)/([^/]+)$""".r
      val path2 = """(.+)/$""".r
      val path3 = """(.+)$""".r
      val path = value match {
        case path1(parts, f) => Path(decodeDirs(parts), Some(decode(f)))
        case path2(parts)    => Path(decodeDirs(parts), None)
        case path3(f)        => Path(Seq(), Some(decode(f)))
        case _               => Path(Seq(), None)
      }
      Right(path)
    }

    def unbind(key: String, path: Path): String = {
      encodeDirs(path.parts) + encode(path.filename.getOrElse(""))
    }

    /**
     * Content should be same as function encode of Path defined in api.helper
     */
    override def javascriptUnbind =
      """
       |function(k,v) {
       |  var fn, ps;
       |  ps = _.chain(v.parts).map(function(p) {
       |    return (encodeURIComponent(p)) + "/";
       |  }).join('').value();
       |  fn = v.filename == null ? '' : encodeURIComponent(v.filename);
       |  return ps + fn;
       |}
      """.stripMargin
  }

  def decodeDirs(parts: String): Array[String] = {
    parts.split("/").filter(_.nonEmpty).map(decode)
  }

  def encodeDirs(parts: Seq[String]): String = {
    ("" /: parts.map(encode))(_ + _ + "/")
  }

  def decode: (String) => String = {
    UriEncoding.decodePathSegment(_, charset)
  }

  def encode: (String) => String = {
    UriEncoding.encodePathSegment(_, charset)
  }

  val charset = "utf-8"
}

trait PathJsonStringifier extends JsonStringifier[Path] {

  implicit val jsonFormat: Format[Path] = Json.format[Path]

  val default: Path = Path()
}