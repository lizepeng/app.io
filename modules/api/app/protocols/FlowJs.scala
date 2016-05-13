package protocols

import helpers.ExtEnumeratee.Enumeratee
import helpers._
import models.cfs.Block._
import models.cfs.CassandraFileSystem._
import models.cfs.Directory._
import models.cfs._
import play.api.i18n.I18nSupport
import play.api.libs.MimeTypes
import play.api.libs.iteratee.{Enumeratee => _, _}
import play.api.libs.json.Json
import play.api.mvc._
import protocols.JsonProtocol.JsonMessage
import security.FileSystemAccessControl._
import security._

import scala.concurrent._
import scala.language._
import scala.util._

/**
 * Server-Side Implementation of flow.js protocol
 *
 * @param filename     if filename specified, it takes priority over the flowFilename one
 * @param permission   file permission
 * @param overwrite    whether overwrite the old one if inode with the same name already exists
 * @param maxLength    maximum size of uploading file
 * @param accept       the types of files are allowed to be uploaded
 * @param form         queryString or data part in multipart/form
 * @param req          Request
 * @param basicPlayApi common play api
 * @param _cfs         cassandra file system api
 * @author zepeng.li@gmail.com
 */
case class FlowJs(
  filename: Option[String] = None,
  permission: Permission = Role.owner.rw,
  overwrite: Boolean = false,
  maxLength: Long = 1024 * 1024 * 1024,
  accept: Seq[String] = Seq(),
  form: Map[String, Seq[String]] = Map()
)(
  implicit
  val req: UserRequest[_],
  val basicPlayApi: BasicPlayApi,
  val _cfs: CassandraFileSystem
) extends BasicPlayComponents
  with DefaultPlayExecutor
  with I18nLoggingComponents
  with I18nSupport {

  def bindFromQueryString(
    implicit req: UserRequest[_]
  ): FlowJs = {
    this.copy(form = req.queryString)
  }

  def bindFromRequestBody(
    implicit req: UserRequest[MultipartFormData[File]]
  ): FlowJs = {
    this.copy(form = req.body.asFormUrlEncoded)
  }

  def identifier = flowParam("flowIdentifier", _.toString)

  /**
   * If filename specified, it takes priority over the flowFilename one.
   *
   * @return the eventually filename that will be used.
   */
  def fileName = filename match {
    case None     => originalFileName
    case Some(fn) => Future.successful(fn)
  }

  def originalFileName = flowParam("flowFilename", _.toString)

  def relativePath = flowParam("flowRelativePath", _.toString)

  def chunkNum = flowParam("flowChunkNumber", _.toInt)

  def chunkSize = flowParam("flowChunkSize", _.toInt)

  def currentChunkSize = flowParam("flowCurrentChunkSize", _.toInt)

  def totalChunks = flowParam("flowTotalChunks", _.toInt)

  def totalSize = flowParam("flowTotalSize", _.toLong)

  def isLastChunk(state: (Int, Long)): Future[Boolean] = {
    val (count, size) = state
    for {
      tc <- totalChunks
      ts <- totalSize
    } yield count == tc && size == ts
  }

  def genTempFileName(id: String, index: Int) = s"$id -- $index"

  def tempFileName = for {
    id <- identifier
    nr <- chunkNum
  } yield genTempFileName(id, nr)

  /**
   * Extract value of the parameter in protocol flow.js, from query string or request body.
   * because of every parameter could missing, this method will return future for convenience.
   *
   * @param key       the key of parameter
   * @param transform the transformation method to convert the value to specific Type of scala
   * @tparam T the Type of the parameter
   * @return return future of the value corresponding specified parameter key.
   */
  def flowParam[T](key: String, transform: String => T): Future[T] = {
    for {
      param <- form.getOrElse(key, Nil).headOption
      value <- Try(transform(param)).toOption
    } yield value
  } match {
    case Some(v) => Future.successful(v)
    case None    => Future.failed(FlowJs.MissingFlowArgument(key))
  }

  override def toString = form.toString()

  /**
   * Enumerator for iterating all uploaded chunks as temp files.
   *
   * @return
   */
  def tempFiles: Enumerator[File] =
    Enumerator.flatten(
      for {
        tc <- totalChunks
        id <- identifier
        temp <- _cfs.temp()
      } yield Enumerator[Int](1 to tc: _*) &>
        Enumeratee.map(genTempFileName(id, _)) &>
        Enumeratee.mapM1(temp.file)
    )

  /**
   * Uploading a chunk of the whole file,
   * this method will be invoked after body parser saving the request body as a temp file.
   *
   * @param chunk      the temp file that body parser parsed and created
   * @param path       target path to upload the file
   * @param onUploaded callback method on success
   * @return
   */
  def upload(chunk: File, path: Path)(
    onUploaded: (Directory, File) => Future[_] = (d, f) => Future.successful(Unit)
  ): Future[Result] = {
    (for {
      _ <- originalFileName.map { fn =>
        val mimeType = MimeTypes.forFileName(fn).getOrElse("")
        if (accept.isEmpty || accept.contains(mimeType)) fn
        else throw FlowJs.NotSupported(fn, accept)
      }
      _ <- totalSize.map { ts =>
        if (ts <= maxLength) ts
        else throw FlowJs.TooLarge(File.pprint(maxLength))
      }
      tmpName <- tempFileName.andThen {
        case Success(fn) => Logger.trace(s"uploading $fn")
      }
      renamed <- currentChunkSize.map(_ == chunk.size).flatMap {
        case false => Future(false) //the upload was stopped by end user
        case true  => chunk.rename(tmpName)
      }.andThen {
        case Success(false) => chunk.delete()
      }
      _result <- {
        if (renamed) checkIfCompleted(path)(onUploaded)
        else {
          //should never occur, only in case that the temp file name was taken.
          Logger.debug(s"renaming to $tmpName failed.")
          Future.successful(Results.InternalServerError)
        }
      }
    } yield _result).andThen {
      case Failure(e: FlowJs.NotSupported) => chunk.delete(); Logger.debug(s"Upload failed, because ${e.reason}")
      case Failure(e: FlowJs.TooLarge)     => chunk.delete(); Logger.debug(s"Upload failed, because ${e.reason}")
      case Failure(e: BaseException)       => chunk.delete(); Logger.debug(s"Upload failed, because ${e.reason}", e)
      case Failure(e: Throwable)           => chunk.delete(); Logger.error(s"Upload failed.", e)
    }.recover {
      case e: FlowJs.NotSupported => Results.UnsupportedMediaType(JsonMessage(e))
      case e: FlowJs.TooLarge     => Results.EntityTooLarge(JsonMessage(e))
      case e: BaseException       => Results.InternalServerError(JsonMessage(e))
      case e: Throwable           => Results.InternalServerError(JsonMessage(e.getMessage))
    }
  }

  /**
   * Once flow.js finish uploading a chunk of the file, it will check if that was the last one.
   * If so flow.js will start concatenating process
   *
   * @param path       target path to upload the file
   * @param onUploaded callback method on success
   * @return
   */
  def checkIfCompleted(path: Path)(
    onUploaded: (Directory, File) => Future[_]
  ): Future[Result] = for {
    name <- fileName
    stat <- tempFiles |>>> summarizer
    last <- isLastChunk(stat)
    _ret <- if (last) concatTempFiles(path, name)(onUploaded) else Future.successful(Results.Created)
  } yield _ret


  /**
   * Summarize all temp files' information, including count of chunks and total size of them.
   *
   * @return (total count of chunks, total size of chunks)
   */
  def summarizer = Iteratee.fold((0, 0L))(
    (stat, f: File) => {
      val (cnt, size) = stat
      (cnt + 1, size + f.size)
    }
  )

  /**
   * Concatenate all chunks to the whole file.
   *
   * @param path       target path to upload the file
   * @param filename   uploading file name
   * @param onUploaded callback method on success
   * @return
   */
  def concatTempFiles(path: Path, filename: String)(
    onUploaded: (Directory, File) => Future[_]
  ): Future[Result] = {

    def concat: Future[Result] = {
      Logger.trace(s"concatenating all temp files of ${path + filename}.")
      for {
        curr <- _cfs.dir(path)
        file <- tempFiles &>
          Enumeratee.mapFlatten[File] { f =>
            f.read() &> Enumeratee.onIterateeDone[BLK](() => f.delete())
          } |>>> curr.save(filename, permission, overwrite, checker = _.w.?)
        _ret <-
        if (file.size <= maxLength) {
          onUploaded(curr, file).map { _ =>
            Logger.trace(s"file: ${path + filename} upload completed.")
            Results.Created(Json.toJson(file)(File.jsonWrites))
          }
        } else Future.successful {

          Logger.debug(FlowJs.TooLarge(File.pprint(maxLength)).reason)
          file.delete()
          Logger.debug(s"deleting file: ${path + filename}.")
          Results.EntityTooLarge(JsonMessage(FlowJs.TooLarge(File.pprint(maxLength))))
        }

      } yield _ret
    }

    concat.recoverWith {
      case e: ChildExists =>
        Logger.debug(s"file: ${path + filename} was created during uploading, clean temp files.")
        (tempFiles |>>> Iteratee.foreach[File](f => f.delete())).map(_ => Results.Ok)
        Future.successful(Results.Ok)
    }
  }

  // -----------------------------------------------------------------------------

  /**
   * Before real uploading the whole file, flow.js will test whether it exists.
   *
   * @param path target path to upload the file
   * @return
   */
  def test(path: Path) = {
    (for {
      name <- fileName
      file <- _cfs.file(path + name) if file.r ?
    } yield {
      Logger.trace(s"file: ${path + name} already exists.")
      Results.Ok
    }).recoverWith {
      case e: Directory.ChildNotFound => testTempFile
    }.andThen {
      case Failure(e: FileSystemAccessControl.Denied) => Logger.trace(s"Test failed, because ${e.reason}")
      case Failure(e: BaseException)                  => Logger.debug(s"Test failed, because ${e.reason}", e)
      case Failure(e: Throwable)                      => Logger.error(s"Test failed.", e)
    }.recover {
      case _: FileSystemAccessControl.Denied => Results.NotFound
      case _: BaseException                  => Results.NotFound
      case _: Throwable                      => Results.InternalServerError
    }
  }

  /**
   * Before real uploading the a chunk of the file, flow.js will test whether it exists.
   *
   * @return
   */
  def testTempFile: Future[Result] = {
    (for {
      size <- currentChunkSize
      temp <- _cfs.temp()
      name <- tempFileName
      file <- temp.file(name)
    } yield {
      if (size == file.size) Results.Ok
      else Results.NoContent
    }).andThen {
      case Failure(e: ChildNotFound)              => Logger.trace(s"Test temp file failed, because ${e.reason}")
      case Failure(e: FlowJs.MissingFlowArgument) => Logger.debug(s"Test temp file failed, because ${e.reason}")
      case Failure(e: BaseException)              => Logger.debug(s"Test temp file failed, because ${e.reason}", e)
      case Failure(e: Throwable)                  => Logger.error(s"Test temp file failed.", e)
    }.recover {
      case _: ChildNotFound              => Results.NoContent
      case _: FlowJs.MissingFlowArgument => Results.NotFound
      case _: BaseException              => Results.NotFound
      case _: Throwable                  => Results.InternalServerError
    }
  }
}

object FlowJs extends ExceptionDefining with CanonicalNamed {

  override def basicName = "flow.js"

  case class MissingFlowArgument(key: String)
    extends BaseException(error_code("missing.flow.argument"))

  case class TooLarge(maxLength: String)
    extends BaseException(error_code("file.too.large"))

  case class NotSupported(filename: String, accept: Seq[String])
    extends BaseException(error_code("file.type.not.supported"))

}