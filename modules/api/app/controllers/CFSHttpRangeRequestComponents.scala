package controllers

import helpers.ExtEnumeratee.Enumeratee
import helpers._
import models.cfs._
import play.api.libs.iteratee.Enumerator
import play.api.mvc._
import protocols._
import security._
import services._

import scala.concurrent.Future
import scala.language._

/**
 * @author zepeng.li@gmail.com
 */
trait CFSHttpRangeRequestComponents {
  self: DefaultPlayExecutor =>

  implicit def basicPlayApi: BasicPlayApi
  implicit def _cfs: CassandraFileSystem

  def bandwidth: BandwidthService
  def bandwidthConfig: BandwidthConfig

  implicit def HttpRangeRequestableFile(file: File): HttpRangeRequestable = new HttpRangeRequestable {
    def size: Long = file.size
    def name: String = file.name
    def range(first: Long, length: Long): Enumerator[Array[Byte]] =
      file.read(first) &>
        Enumeratee.take(length.toInt) &>
        bandwidth.LimitTo(bandwidthConfig.stream)
    def whole: Enumerator[Array[Byte]] =
      file.read() &>
        bandwidth.LimitTo(bandwidthConfig.download)
  }

  case class HttpCached(path: Path)(
    implicit val req: UserRequest[_]
  ) {
    def orElse(block: File => Result): Future[Result] = {
      import FileSystemAccessControl._
      (for {
        file <- _cfs.file(path) if file.r ?
      } yield file).map {
        HttpCaching(block)
      }.recover {
        case e: FileSystemAccessControl.Denied => Results.Forbidden
        case e: BaseException                  => Results.NotFound
      }
    }
  }

}