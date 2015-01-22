package views.html

import _root_.helpers.syntax.PolarQuestion
import play.api.http.ContentTypes
import play.api.libs.MimeTypes

/**
 * @author zepeng.li@gmail.com
 */
package object files {

  implicit class RichFile(val f: models.cfs.File) extends AnyVal {

    def icon: String = {
      ext.map {
        case "png" | "jpg" | "gif"         => "fa-file-image-o"
        case ext if audio.?                => "fa-file-audio-o"
        case "txt"                         => "fa-file-text-o"
        case ext if pdf.?                  => "fa-file-pdf-o"
        case "doc" | "docx"                => "fa-file-word-o"
        case "ppt"                         => "fa-file-powerpoint-o"
        case "xls" | "xlsx"                => "fa-file-excel-o"
        case ext if video.?                => "fa-file-video-o"
        case "zip" | "rar" | "7z"          => "fa-file-archive-o"
        case "tar" | "gz" | "bz"           => "fa-file-archive-o"
        case "html" | "css" | "js" | "xml" => "fa-file-code-o"
        case _                             => "fa-file-o"
      }.get
    }

    def mimeType: String = {
      MimeTypes.forFileName(f.name).getOrElse(ContentTypes.BINARY)
    }

    def ext: Option[String] = {
      f.name.split('.').takeRight(1).headOption
    }

    def pdf = new PolarQuestion {
      def ? : Boolean = ext.exists {
        case "pdf" | "ps" => true
        case _            => false
      }
    }

    def audio = new PolarQuestion {
      def ? : Boolean = ext.exists {
        case "mp3" | "wma" | "m4a" => true
        case _                     => false
      }
    }

    def video = new PolarQuestion {
      def ? : Boolean = ext.exists {
        case "mp4" | "mkv" => true
        case _             => false
      }
    }

    def size_pp: String = {
      f.size match {
        case s if s > (1L << 40) => f"${s / 1e12}%7.3f TB"
        case s if s > (1L << 30) => f"${s / 1e09}%6.2f GB"
        case s if s > (1L << 20) => f"${s / 1e06}%5.1f MB"
        case s if s > (1L << 10) => f"${s / 1e03}%3f KB"
        case s if s > 0          => f"$s%3d bytes"
        case _                   => "Zero bytes"
      }
    }
  }

}