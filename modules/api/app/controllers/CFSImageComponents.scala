package controllers

import com.sksamuel.scrimage.Image
import com.sksamuel.scrimage.nio._
import helpers._
import models.User
import models.cfs.Block._
import models.cfs._
import play.api.libs.iteratee._
import protocols.HttpDownloadable
import security.FileSystemAccessControl._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */

trait CFSImageComponents extends CFSDownloadComponents {
  self: DefaultPlayExecutor =>

  case class CImage(file: File)(
    manipulator: Image => Image = i => i,
    writer: ImageWriter = PngWriter.MaxCompression
  ) {

    def content: Future[Array[Byte]] =
      (file.read() |>>> Iteratee.consume[BLK]()).map {
        origin => manipulator(Image(origin)).bytes(writer)
      }

    def enumerator: Enumerator[Array[Byte]] =
      Enumerator.flatten(
        content.map(Enumerator(_))
      )

    def downloadable: Future[HttpDownloadable] =
      content.map { bytes =>
        new HttpDownloadable {
          def size = bytes.length
          def name = file.name
          def whole = Enumerator(bytes) &>
            bandwidth.LimitTo(bandwidthConfig.download)
        }
      }
  }

  case class Thumbnail(file: File, width: Int, height: Int) {

    def name: String = Thumbnail.generateName(file.name, width, height)

    def saveInto(dir: Directory)(
      implicit user: User
    ): Future[File] = {
      CImage(file)(_.scaleTo(width, height)).enumerator |>>>
        dir.save(name, overwrite = true, _.w.?)
    }
  }

  object Thumbnail {
    def generate(curr: Directory, file: File)(
      implicit user: User
    ): Future[Seq[File]] = {
      Future.sequence(
        for (i <- Array(32, 64, 128, 256, 512)) yield {
          Thumbnail(file, i, i).saveInto(curr)
        }
      )
    }

    def choose(filename: String, size: Int) = size match {
      case s if s < 0    => filename
      case s if s <= 32  => generateName(filename, 32, 32)
      case s if s <= 64  => generateName(filename, 64, 64)
      case s if s <= 128 => generateName(filename, 128, 128)
      case s if s <= 256 => generateName(filename, 256, 256)
      case _             => generateName(filename, 512, 512)
    }

    def generateName(filename: String, width: Int, height: Int): String = {
      val filenamePattern = """(\w+).(\w+)""".r
      filename match {
        case filenamePattern(base, ext) => s"$base${width}x$height.$ext"
        case name                       => s"${width}x$height"
      }
    }
  }

}