package models.misc

import java.util.Base64

import play.api._
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.util.parsing.combinator._

/**
 * @author zepeng.li@gmail.com
 */
case class DataURI(
  mime_type: String = DataURI.defaultMimeType,
  charset: String = DataURI.defaultCharSet,
  base64: Boolean = true,
  data: String = ""
) {

  override def toString = {
    val c = if (charset != DataURI.defaultCharSet) s";$charset" else ""
    val b = if (base64) ";base64" else ""
    s"data:$mime_type$c$b,$data"
  }
}

object DataURI {

  def defaultMimeType = http.MimeTypes.TEXT
  def defaultCharSet = "US-ASCII"

  def empty = DataURI()

  def from(filename: String, bytes: Array[Byte]) = DataURI(
    mime_type = libs.MimeTypes.forFileName(filename).getOrElse(http.MimeTypes.BINARY),
    base64 = true,
    data = Base64.getEncoder.encodeToString(bytes)
  )

  /**
   * Data URI Syntax:
   * {{{
   *   data:[<media type>][;charset=<character set>][;base64],<data>
   * }}}
   */
  object Parsers extends RegexParsers {

    def dataURI = ("data:" ~> mime ~ charset ~ base64 ~ data) ^^ {
      case ~(~(~(m, c), b), d) => DataURI(m, c, b, d)
    }
    def mime = """\w+/\w+""".r ^^ (x => libs.MimeTypes.types.values.find(_ == x).getOrElse(defaultMimeType))
    def charset = opt(";charset=" ~> """\w+""".r) ^^ (_.getOrElse(defaultCharSet))
    def base64 = opt(";base64") ^^ (_.isDefined)
    def data = "," ~> ".*".r
  }

  def parse(string: String) = Parsers.parseAll(Parsers.dataURI, string)

  implicit val jsonFormat = Format.of[String].inmap[DataURI](
    x => DataURI.parse(x).getOrElse(DataURI.empty), _.toString
  )
}