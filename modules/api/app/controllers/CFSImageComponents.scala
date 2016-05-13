package controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.sksamuel.scrimage.Image
import com.sksamuel.scrimage.nio._
import controllers.CFSBodyParser.MissingFile
import helpers._
import models.User
import models.cfs.Block._
import models.cfs.CassandraFileSystem._
import models.cfs._
import play.api.i18n.I18nSupport
import play.api.libs.iteratee._
import play.api.libs.streams.Streams
import play.api.mvc._
import protocols.JsonProtocol.JsonMessage
import protocols._
import security.FileSystemAccessControl._
import security._

import scala.concurrent._
import scala.util._

/**
 * @author zepeng.li@gmail.com
 */
trait CFSImageComponents extends CFSDownloadComponents {
  self: DefaultPlayExecutor
    with CFSDownloadComponents
    with I18nSupport
    with I18nLogging =>

  case class CFSImage(file: File)(
    manipulator: Image => Image = i => i,
    writer: ImageWriter = PngWriter.MaxCompression
  ) {

    def content: Future[BLK] =
      (file.read() |>>> Iteratee.consume[BLK]()).map { origin =>
        //Since the bytes may not be a image
        Try(manipulator(Image(origin.toArray))) match {
          case Success(img: Image)   => ByteString.fromArray(img.bytes(writer))
          case Failure(e: Throwable) => ByteString.empty
        }
      }

    def enumerator: Enumerator[BLK] =
      Enumerator.flatten(
        content.map(Enumerator(_))
      )

    def downloadable: Future[HttpDownloadable] =
      content.map { bytes =>
        new HttpDownloadable {
          def size = bytes.length
          def name = file.name
          def whole = Source.fromPublisher(
            Streams.enumeratorToPublisher(
              Enumerator(bytes) &>
                bandwidth.LimitTo(bandwidthConfig.download)
            )
          )
        }
      }
  }

  object CFSImage {

    def show(filePath: User => Path, size: Int = 0)(
      implicit errorHandler: UserActionExceptionHandler
    ): UserRequest[AnyContent] => Future[Result] = implicit req => {
      val path = filePath(req.user)
      val thumbnail = path.filename.map(Thumbnail.choose(_, size))
      import security._
      (for {
        result <- CFSHttpCaching(path + thumbnail).async { file =>
          CFSImage(file)().downloadable.map {
            send(_, _ => path.filename.getOrElse(""), inline = true)
          }
        }
      } yield {
        result
      }).recover {
        case e: FileSystemAccessControl.Denied => errorHandler.onFilePermissionDenied(req)
      }
    }

    def test(
      filePath: User => Path
    ): UserRequest[AnyContent] => Future[Result] = implicit req => {
      val path = filePath(req.user)
      FlowJs(filename = path.filename).bindFromQueryString.test(path)
    }

    def upload(
      filePath: User => Path,
      permission: Permission = Role.owner.rw,
      overwrite: Boolean = false,
      maxLength: Long = 16 * 1024 * 1024,
      accept: Seq[String] = Seq()
    ): UserRequest[MultipartFormData[File]] => Future[Result] = implicit req => {
      val path = filePath(req.user)
      (req.body.file("file").map(_.ref) match {
        case Some(chunk) => FlowJs(
          filename = path.filename,
          permission = permission,
          overwrite = overwrite,
          maxLength = maxLength,
          accept = accept
        ).bindFromRequestBody.upload(chunk, path.parent) { (curr, file) =>
          Thumbnail.generate(curr, file)(req.user)
        }
        case None        => throw CFSBodyParser.MissingFile()
      }).andThen {
        case Failure(e: MissingFile) => Logger.warn(e.reason, e)
      }.recover {
        case e: CFSBodyParser.MissingFile => Results.BadRequest(JsonMessage(e))
      }
    }
  }

  case class Thumbnail(file: File, width: Int, height: Int) {

    def name: String = Thumbnail.generateName(file.name, width, height)

    def saveInto(dir: Directory)(
      implicit user: User
    ): Future[File] = {
      CFSImage(file)(_.scaleTo(width, height)).enumerator |>>>
        dir.save(name, file.permission, overwrite = true, checker = _.w.?)
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
      case s if s <= 0   => filename
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