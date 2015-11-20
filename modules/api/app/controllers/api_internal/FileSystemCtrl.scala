package controllers.api_internal

import controllers._
import helpers._
import models.cfs._
import play.api.i18n._
import play.api.libs.json.Json
import play.api.mvc._
import protocols.JsonProtocol.JsonMessage
import protocols._
import security.FileSystemAccessControl._
import security._
import services._

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
  with I18nSupport
  with AppConfigComponents
  with RateLimitConfigComponents
  with BandwidthConfigComponents
  with CFSStreamComponents
  with Logging {

  def download(path: Path, inline: Boolean) =
    UserAction(_.Show).async { implicit req =>
      CFSHttpCaching(path) apply (HttpDownloadResult.send(_, inline = inline))
    }

  def stream(path: Path) =
    UserAction(_.Show).async { implicit req =>
      CFSHttpCaching(path) apply (HttpStreamResult.stream(_))
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
      Flow().bindFromQueryString.test(path)
    }

  def create(path: Path) =
    UserAction(_.Create).async(CFSBodyParser(_ => path)) { implicit req =>
      (req.body.file("file").map(_.ref) match {
        case Some(file) => Flow().bindFromRequestBody.upload(file, path)()
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
}

object FileSystemCtrl extends Secured(CassandraFileSystem)