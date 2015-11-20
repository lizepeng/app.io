package controllers

import helpers._
import models.cfs._
import play.api.i18n._
import play.api.mvc.Controller
import protocols._
import security._
import services._
import views._

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
  with BasicPlayComponents
  with UserActionComponents
  with DefaultPlayExecutor
  with BandwidthConfigComponents
  with CFSStreamComponents
  with I18nSupport {

  def index(path: Path, pager: Pager) =
    UserAction(_.Index, _.Create, _.Destroy).apply { implicit req =>
      Ok(html.file_system.index(if (path.isRoot) path / req.user.id.toString else path, pager))
    }

  def show(path: Path) =
    UserAction(_.Show).async { implicit req =>
      (for {
        file <- _cfs.file(path)
      } yield file).map { file =>
        Ok(html.file_system.show(file))
      }.recover {
        case e: FileSystemAccessControl.Denied => Forbidden
        case e: BaseException                  => NotFound
      }
    }

  def download(path: Path, inline: Boolean) =
    UserAction(_.Show).async { implicit req =>
      CFSHttpCaching(path) apply (HttpDownloadResult.send(_, inline = inline))
    }

  def stream(path: Path) =
    UserAction(_.Show).async { implicit req =>
      CFSHttpCaching(path) apply (HttpStreamResult.stream(_))
    }
}

object FileSystemCtrl
  extends Secured(CassandraFileSystem)
  with CanonicalNameBasedMessages
  with ViewMessages