package views.utils

/**
 * @author zepeng.li@gmail.com
 */
object FileIcon {
  def apply(name: String): String = {
    name.split('.').takeRight(1).headOption.map {
      case "txt"                         => "fa-file-text-o"
      case "pdf"  |"ps"                  => "fa-file-pdf-o"
      case "png"  |"jpg"  |"gif"         => "fa-file-image-o"
      case "mp4"  |"mkv"                 => "fa-file-video-o"
      case "mp3"  |"wma"                 => "fa-file-audio-o"
      case "zip"  |"rar"  |"7z"          => "fa-file-archive-o"
      case "tar"  |"gz"   |"bz"          => "fa-file-archive-o"
      case "doc"  |"docx"                => "fa-file-word-o"
      case "xls"  |"xlsx"                => "fa-file-excel-o"
      case "ppt"                         => "fa-file-powerpoint-o"
      case "html" |"css"  |"js"  |"xml"  => "fa-file-code-o"
      case _                             => "fa-file-o"
    }.get
  }
}

object FileSize {

  def apply(size: Int): String = {
    size match {
      case x if x > (1L << 40) => s"${size >> 40} TB"
      case x if x > (1L << 30) => val n = size >> 23; s"${n/128}.${n%128} GB"
      case x if x > (1L << 20) => val n = size >> 16; s"${n/16}.${n%16} MB"
      case x if x > (1L << 10) => s"${size >> 10} KB"
      case x if x > 0          => s"$size bytes"
      case _                   => "Zero bytes"
    }
  }
}