package controllers

import helpers._
import models._
import models.cfs.{CassandraFileSystem => CFS, _}
import play.api.libs.streams.Streams
import play.api.mvc.BodyParsers._
import play.api.mvc.BodyParsers.parse._
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart._
import security.FileSystemAccessControl._
import security._
import services._

import scala.concurrent._
import scala.language.postfixOps
import scala.util.Failure

/**
 * BodyParser for uploading file to cassandra file system
 *
 * @author zepeng.li@gmail.com
 */
case class CFSBodyParser(
  path: User => Path,
  dirPermission: CFS.Permission = CFS.Role.owner.rwx
)(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _cfs: CFS,
  val bandwidth: BandwidthService,
  val bandwidthConfig: BandwidthConfig,
  val errorHandler: BodyParserExceptionHandler
) extends BasicPlayComponents
  with DefaultPlayExecutor
  with I18nLoggingComponents {

  def parser(req: RequestHeader)(
    implicit user: User
  ): Future[BodyParser[MultipartFormData[File]]] = {
    (for {
      temp <- _cfs.temp(dirPermission)
      dest <- _cfs.dir(path(user)) if dest.w ?
    } yield {
      multipartFormData(saveTo(temp))
    }).andThen {
      case Failure(e: Directory.NotFound)             => Logger.debug(s"CFSBodyParser failed, because ${e.reason}")
      case Failure(e: Directory.ChildNotFound)        => Logger.debug(s"CFSBodyParser failed, because ${e.reason}")
      case Failure(e: FileSystemAccessControl.Denied) => Logger.debug(s"CFSBodyParser failed, because ${e.reason}")
      case Failure(e: BaseException)                  => Logger.error(s"CFSBodyParser failed, because ${e.reason}", e)
      case Failure(e: Throwable)                      => Logger.error(s"CFSBodyParser failed.", e)
    }.recover {
      case _: Directory.NotFound             => parse.error(Future.successful(errorHandler.onPathNotFound(req)))
      case _: Directory.ChildNotFound        => parse.error(Future.successful(errorHandler.onPathNotFound(req)))
      case _: FileSystemAccessControl.Denied => parse.error(Future.successful(errorHandler.onFilePermissionDenied(req)))
      case _: BaseException                  => parse.error(Future.successful(errorHandler.onFilePermissionDenied(req)))
      case _: Throwable                      => parse.error(Future.successful(errorHandler.onThrowable(req)))
    }
  }

  def saveTo(dir: Directory)(implicit user: User): FilePartHandler[File] = {
    case FileInfo(partName, filename, contentType) =>
      Streams.iterateeToAccumulator(
        (bandwidth.LimitTo(bandwidthConfig.upload) &>> dir.save()).map { file =>
          FilePart(partName, filename, contentType, file)
        }
      )
  }
}

object CFSBodyParser
  extends CanonicalNamed
    with ExceptionDefining {

  override def basicName: String = CFS.basicName

  case class MissingFile()
    extends BaseException(error_code("missing.file"))

}