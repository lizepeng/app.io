package controllers

import helpers._
import models.cfs._
import play.api.mvc._
import protocols._
import security._
import services._

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */

trait CFSDownloadComponents extends HttpDownloadResult {
  self: DefaultPlayExecutor =>

  implicit def basicPlayApi: BasicPlayApi
  implicit def _cfs: CassandraFileSystem

  def bandwidth: BandwidthService
  def bandwidthConfig: BandwidthConfig

  case class CFSHttpCaching(path: Path)(
    implicit val req: UserRequest[_]
  ) {
    def apply(block: File => Result): Future[Result] =
      async(b => Future.successful(block(b)))

    def async(block: File => Future[Result]): Future[Result] = {
      import FileSystemAccessControl._
      (for {
        file <- _cfs.file(path) if file.r ?
      } yield file).flatMap {
        HttpCaching.async(block)
      }.recover {
        case e: FileSystemAccessControl.Denied => Results.Forbidden
        case e: BaseException                  => Results.NotFound
      }
    }
  }

}