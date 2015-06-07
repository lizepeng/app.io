import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
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
    //    ChatActor.startRegion(Akka.system)
  }

  //TODO lifecycle
  override def onStop(app: Application) = {
    //    Logger.info("Shutting down cassandra...")
    //    Logger.info("Shutting down elastic search...")
    //    Logger.info("System shutdown...")
  }

  override def onBadRequest(
    request: RequestHeader,
    error: String
  ): Future[Result] = {
    if (request.uri.startsWith("/api")) {
      Future.successful(Results.NotFound)
    }
    else super.onBadRequest(request, error)
  }

  override def onHandlerNotFound(
    request: RequestHeader
  ): Future[Result] = {
    if (request.uri.startsWith("/api")) {
      Future.successful(Results.NotFound)
    }
    else super.onHandlerNotFound(request)
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