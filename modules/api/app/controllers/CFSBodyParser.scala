package controllers

import helpers._
import models._
import models.cfs._
import play.api.mvc.BodyParsers._
import play.api.mvc.BodyParsers.parse._
import play.api.mvc._
import play.core.parsers.Multipart._
import security.FileSystemAccessControl._
import security.ModulesAccessControl._
import security._
import services._

import scala.concurrent._
import scala.language.postfixOps

/**
 * BodyParser for uploading file to our cassandra file system
 *
 * @author zepeng.li@gmail.com
 */
case class CFSBodyParser(
  path: User => Path,
  access: Access = AccessDefinition.Create,
  pamBuilder: BasicPlayApi => PAM = AuthenticateBySession,
  onUnauthorized: RequestHeader => Result = req => Results.NotFound,
  onPermDenied: RequestHeader => Result = req => Results.NotFound,
  onPathNotFound: RequestHeader => Result = req => Results.NotFound,
  onFilePermDenied: RequestHeader => Result = req => Results.NotFound,
  onBaseException: RequestHeader => Result = req => Results.NotFound
)(
  implicit
  val resource: CheckedModule,
  val basicPlayApi: BasicPlayApi,
  val _users: Users,
  val _accessControls: AccessControls,
  val _cfs: CassandraFileSystem,
  val bandwidth: BandwidthService,
  val bandwidthConfig: BandwidthConfig
) extends PermissionCheckedBodyParser[MultipartFormData[File]] {

  override def parser(req: RequestHeader)(
    implicit user: User
  ): Future[BodyParser[MultipartFormData[File]]] = {
    def saveTo(dir: Directory)(implicit user: User) = {
      handleFilePart {
        case FileInfo(partName, fileName, contentType) =>
          bandwidth.LimitTo(bandwidthConfig.upload) &>> dir.save()
      }
    }

    (for {
      temp <- _cfs.temp(user)
      dest <- _cfs.dir(path(user)) if dest.w ?
    } yield temp).map { case temp =>
      multipartFormData(saveTo(temp)(user))
    }.recover {
      case _: Directory.NotFound | _: Directory.ChildNotFound =>
        parse.error(Future.successful(onPathNotFound(req)))
      case _: FileSystemAccessControl.Denied                  =>
        parse.error(Future.successful(onFilePermDenied(req)))
    }
  }

  override def pam: PAM = pamBuilder(basicPlayApi)
}

object CFSBodyParser
  extends CanonicalNamed
  with ExceptionDefining {

  override def basicName: String = CassandraFileSystem.basicName

  case class MissingFile()
    extends BaseException(error_code("missing.file"))

}