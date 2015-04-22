import models._
import models.cfs._
import models.sys.SysConfig
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import play.api.{Application, _}
import play.filters.gzip.GzipFilter

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object Global
  extends WithFilters(
    new GzipFilter(
      shouldGzip = (req, resp) =>
        resp.headers.get(HeaderNames.CONTENT_TYPE).exists {
          case s if s.startsWith(MimeTypes.JSON) => true
          case s if s.startsWith(MimeTypes.HTML) => true
          case _                                 => false
        }
    )
  )
  with GlobalSettings {

  override def onStart(app: Application) = {
    Logger.info("System has started")
    Schemas.create
  }

  override def onStop(app: Application) = {
    Logger.info("Shutting down cassandra...")

    Schemas.shutdown()

    //System
    SysConfig.shutdown()
    AccessControl.shutdown()
    SessionData.shutdown()

    //User
    User.shutdown()
    UserByEmail.shutdown()
    Group.shutdown()

    //CFS
    INode.shutdown()
    IndirectBlock.shutdown()
    Block.shutdown()

    ExpirableLink.shutdown()
    EmailTemplate.shutdown()

    //CFS View
    File.shutdown()
    Directory.shutdown()

    Logger.info("System shutdown...")
  }

  override def onBadRequest(
    request: RequestHeader,
    error: String
  ): Future[Result] = {
    if (request.uri.startsWith("/api"))
      Future.successful {
        Results.BadRequest(Json.obj("message" -> error))
      }
    else super.onBadRequest(request, error)
  }

  override def doFilter(next: EssentialAction): EssentialAction = {
    Filters(super.doFilter(next), loggingFilter)
  }

  val loggingFilter = Filter { (nextFilter, header) =>
    val startTime = System.currentTimeMillis
    nextFilter(header).map { result =>
      val endTime = System.currentTimeMillis
      val requestTime = endTime - startTime
      if (!header.uri.contains("assets")) {
        Logger.trace(
          f"${result.header.status}, took $requestTime%4d ms, ${header.method} ${header.uri}"
        )
      }
      result.withHeaders("Request-Time" -> requestTime.toString)
    }
  }
}