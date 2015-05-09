package views.html

import _root_.helpers.syntax._
import play.api.http.ContentTypes
import play.api.libs.MimeTypes

/**
 * @author zepeng.li@gmail.com
 */
package object files {

  implicit class RichFile(val f: models.cfs.File) extends AnyVal {

    def icon: String = {
      ext.map {
        case ext if image.?                        => "fa-file-image-o"
        case ext if audio.?                        => "fa-file-audio-o"
        case ci"txt"                               => "fa-file-text-o"
        case ext if pdf.?                          => "fa-file-pdf-o"
        case ci"doc"  | ci"docx"                   => "fa-file-word-o"
        case ci"ppt"                               => "fa-file-powerpoint-o"
        case ci"xls"  | ci"xlsx"                   => "fa-file-excel-o"
        case ext if video.?                        => "fa-file-video-o"
        case ci"zip"  | ci"rar" | ci"7z"           => "fa-file-archive-o"
        case ci"tar"  | ci"gz"  | ci"bz"           => "fa-file-archive-o"
        case ci"html" | ci"css" | ci"js" | ci"xml" => "fa-file-code-o"
        case _                                     => "fa-file-o"
      }.get
    }

    def mimeType: String = {
      MimeTypes.forFileName(f.name).getOrElse(ContentTypes.BINARY)
    }

    def ext: Option[String] = {
      f.name.split('.').takeRight(1).headOption
    }

    def image = new PolarQuestion {
      def ? : Boolean = ext.exists {
        case ci"png" | ci"jpg" | ci"gif" => true
        case _                           => false
      }
    }

    def pdf = new PolarQuestion {
      def ? : Boolean = ext.exists {
        case ci"pdf" | ci"ps" => true
        case _                => false
      }
    }

    def audio = new PolarQuestion {
      def ? : Boolean = ext.exists {
        case ci"mp3" | ci"wma" | ci"m4a" => true
        case _                           => false
      }
    }

    def video = new PolarQuestion {
      def ? : Boolean = ext.exists {
        case ci"mp4" | ci"mkv" | ci"mov" => true
        case _                           => false
      }
    }

    def size_pp: String = {
      f.size match {
        case s if s > (1L << 40) => f"${s / 1e12}%7.3f TB"
        case s if s > (1L << 30) => f"${s / 1e09}%6.2f GB"
        case s if s > (1L << 20) => f"${s / 1e06}%5.1f MB"
        case s if s > (1L << 10) => f"${s / 1000}%3d KB"
        case s if s > 0          => f"$s%3d bytes"
        case _                   => "Zero bytes"
      }
    }
  }

}