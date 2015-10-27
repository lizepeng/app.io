package controllers.api_internal

import controllers._
import helpers.ExtEnumeratee._
import helpers._
import models.cfs._
import play.api.http.ContentTypes
import play.api.i18n._
import play.api.libs.MimeTypes
import play.api.libs.iteratee.{Enumeratee => _, _}
import play.api.libs.json.Json
import play.api.mvc._
import protocols.JsonProtocol.JsonMessage
import protocols._
import security.FileSystemAccessControl._
import security._
import services._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Failure

/**
 * @author zepeng.li@gmail.com
 */
class FileSystemCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val userActionRequired: UserActionRequired,
  val bandwidth: BandwidthService,
  val _cfs: CassandraFileSystem
)
  extends Secured(FileSystemCtrl)
  with Controller
  with LinkHeader
  with BasicPlayComponents
  with UserActionComponents
  with DefaultPlayExecutor
  with ExceptionDefining
  with I18nSupport
  with AppConfigComponents
  with RateLimitConfigComponents
  with BandwidthConfigComponents
  with Logging {


  def download(path: Path, inline: Boolean) =
    UserAction(_.Show).async { implicit req =>
      withCached(path) { file =>
        val stm = streamWhole(file)
        if (inline) stm
        else stm.withHeaders(
          CONTENT_DISPOSITION ->
            s"""attachment; filename="${Path.encode(file.name)}" """
        )
      }
    }

  def stream(path: Path) =
    UserAction(_.Show).async { implicit req =>
      withCached(path) { file =>
        val size = file.size
        val byte_range_spec = """bytes=(\d+)-(\d*)""".r

        req.headers.get(RANGE).flatMap[(Long, Option[Long])] {
          case byte_range_spec(first, last) =>
            val byteRange: Some[(Long, Option[Long])] = Some(
              first.toLong,
              Some(last).filter(_.nonEmpty).map(_.toLong)
            )
            byteRange
          case _                            => None
        }.filter {
          case (first, lastOpt) => lastOpt.isEmpty || lastOpt.get >= first
        }.map {
          case (first, lastOpt) if first < size  => streamRange(file, first, lastOpt)
          case (first, lastOpt) if first >= size => invalidRange(file)
        }.getOrElse {
          streamWhole(file).withHeaders(ACCEPT_RANGES -> "bytes")
        }
      }
    }

  def index(path: Path, pager: Pager) =
    UserAction(_.Index).async { implicit req =>
      (for {
        curr <- _cfs.dir(path) if curr.rx.?
        page <- curr.list(pager)
      } yield page).map { page =>
        Ok(Json.toJson(page.elements)).withHeaders(
          linkHeader(page, routes.FileSystemCtrl.index(path, _))
        )
      }.recover {
        case e: FileSystemAccessControl.Denied => Forbidden
        case e: BaseException                  => NotFound
      }
    }

  def test(path: Path) =
    UserAction(_.Show).async { implicit req =>
      Flow.bindFromQueryString().test(path)
    }

  def create(path: Path) =
    UserAction(_.Create).async(CFSBodyParser(_ => path)) { implicit req =>
      (req.body.file("file").map(_.ref) match {
        case Some(file) => Flow.bindFromRequest().upload(file, path)
        case None       => throw CFSBodyParser.MissingFile()
      }).andThen {
        case Failure(e: CFSBodyParser.MissingFile)      => Logger.warn(e.message)
        case Failure(e: FileSystemAccessControl.Denied) => Logger.debug(e.message)
        case Failure(e: BaseException)                  => Logger.error(e.message)
      }.recover {
        case e: BaseException => NotFound(JsonMessage(e))
      }
    }

  def destroy(path: Path, clear: Boolean) =
    UserAction(_.Destroy).async { implicit req =>
      (path.filename match {
        case Some(_) => for {
          file <- _cfs.file(path) if file.w.?
          ____ <- file.delete()
        } yield Unit
        case None    => for {
          dir <- _cfs.dir(path) if dir.w.?
          ___ <- if (clear) dir.clear()
          else dir.delete(recursive = true)
        } yield Unit
      }).map {
        _ => NoContent
      }.recover {
        case e: FileSystemAccessControl.Denied => Forbidden
        case e: Directory.ChildNotFound        => NotFound
      }
    }

  private def invalidRange(file: File): Result = {
    Result(
      ResponseHeader(
        REQUESTED_RANGE_NOT_SATISFIABLE,
        Map(
          CONTENT_RANGE -> s"*/${file.size}",
          CONTENT_TYPE -> contentTypeOf(file)
        )
      ),
      Enumerator.empty
    )
  }

  private def streamRange(
    file: File,
    first: Long,
    lastOpt: Option[Long]
  ): Result = {
    val end = lastOpt.filter(_ < file.size).getOrElse(file.size - 1)
    Result(
      ResponseHeader(
        PARTIAL_CONTENT,
        Map(
          ACCEPT_RANGES -> "bytes",
          CONTENT_TYPE -> contentTypeOf(file),
          CONTENT_RANGE -> s"bytes $first-$end/${file.size}",
          CONTENT_LENGTH -> s"${end - first + 1}"
        )
      ),
      file.read(first) &>
        Enumeratee.take((end - first + 1).toInt) &>
        bandwidth.LimitTo(bandwidthConfig.stream)

    )
  }

  private def streamWhole(file: File): Result = Result(
    ResponseHeader(
      OK,
      Map(
        CONTENT_TYPE -> contentTypeOf(file),
        CONTENT_LENGTH -> s"${file.size}"
      )
    ),
    file.read() &> bandwidth.LimitTo(bandwidthConfig.download)
  )

  private def withCached(path: Path)(block: File => Result)(
    implicit req: UserRequest[_], messages: Messages
  ): Future[Result] = {
    (for {
      file <- _cfs.file(path) if file.r ?
    } yield file).map {
      NotModifiedOrElse(block)(req, messages)
    }.recover {
      case e: FileSystemAccessControl.Denied => Forbidden
      case e: BaseException                  => NotFound
    }
  }

  private def contentTypeOf(inode: INode): String = {
    MimeTypes.forFileName(inode.name).getOrElse(ContentTypes.BINARY)
  }

}

object FileSystemCtrl extends Secured(CassandraFileSystem)