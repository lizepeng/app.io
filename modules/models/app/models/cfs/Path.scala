package models.cfs

import helpers.JsonStringifier
import play.api.libs.json.{Format, Json}
import play.api.mvc.PathBindable
import play.utils.UriEncoding

/**
 * @author zepeng.li@gmail.com
 */
case class Path(segments: Seq[String] = Seq(), filename: Option[String] = None) {

  override def toString = segments.mkString("", "/", "/") + filename.getOrElse("")

  def /(dir: String) = if (dir == ".") this else copy(segments ++ Seq(dir))

  def /(sub: Path) = copy(segments ++ sub.segments, sub.filename)

  def /:(dir: String) = if (dir == ".") this else copy(Seq(dir) ++ segments)

  def +(filename: String) = copy(filename = Some(filename))

  def headOption: Option[String] = segments.headOption

  def tail = if (segments.isEmpty) this else Path(segments.tail, filename)

  def compact = copy(
    segments = segments.foldRight(List[String]()) {
      case (curr, list) => list match {
        case ".." :: tail => tail
        case _            => curr match {
          case "." => list
          case _   => curr :: list
        }
      }
    }
  )

  def subPaths = {
    for (i <- 0 to segments.size)
      yield Path(segments.take(i), None)
  } ++ {
    if (filename.nonEmpty) Seq(this) else Seq.empty
  }

  def isRoot = segments.isEmpty && filename.isEmpty
}

object Path extends PathJsonStringifier {

  def root = Path()

  implicit def bindablePath: PathBindable[Path] = new PathBindable[Path] {

    def bind(key: String, value: String): Either[String, Path] = {
      val path1 = """(.+)/([^/]+)$""".r
      val path2 = """(.+)/$""".r
      val path3 = """(.+)$""".r
      val path = value match {
        case path1(segments, f) => Path(decodeDirs(segments), Some(decode(f)))
        case path2(segments)    => Path(decodeDirs(segments), None)
        case path3(f)           => Path(Seq(), Some(decode(f)))
        case _                  => Path(Seq(), None)
      }
      Right(path)
    }

    def unbind(key: String, path: Path): String = {
      encodeDirs(path.segments) + encode(path.filename.getOrElse(""))
    }

    /**
     * Content should be same as function encode of Path defined in api.helper
     */
    override def javascriptUnbind =
      """
       |function(k,v) {
       |  var fn, ps;
       |  ps = _.chain(v.segments).map(function(p) {
       |    return (encodeURIComponent(p)) + "/";
       |  }).join('').value();
       |  fn = v.filename == null ? '' : encodeURIComponent(v.filename);
       |  return ps + fn;
       |}
      """.stripMargin
  }

  def decodeDirs(segments: String): Array[String] = {
    segments.split("/").filter(_.nonEmpty).map(decode)
  }

  def encodeDirs(segments: Seq[String]): String = {
    ("" /: segments.map(encode))(_ + _ + "/")
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