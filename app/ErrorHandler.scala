import helpers._
import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.i18n._
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router
import play.core.SourceMapper
import protocols.JsonProtocol._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
class ErrorHandler(
  val environment: Environment,
  val configuration: Configuration,
  sourceMapper: Option[SourceMapper],
  router: => Option[Router]
) extends DefaultHttpErrorHandler(environment, configuration, sourceMapper, router)
  with I18nComponents
  with I18nLoggingComponents
  with I18nSupport {

  override def onClientError(req: RequestHeader, status: Int, message: String) = {
    import play.api.http.Status._
    import play.api.mvc.Results._

    if (isFromApi(req)) Future.successful {
      status match {
        case BAD_REQUEST => BadRequest(JsonMessage(message))
        case FORBIDDEN   => Forbidden(JsonMessage(message))
        case _           => NotFound(JsonMessage(message))
      }
    }
    else super.onClientError(req, status, message)
  }

  override def onServerError(req: RequestHeader, ex: Throwable) = ex match {
    case e: BaseException =>
      implicit val request = req
      Logger.debug(e.reason, e)
      if (isFromApi(req)) Future.successful(NotFound(JsonMessage(e)))
      else Future.successful(NotFound(views.html.defaultpages.notFound(req.method, s"${req.uri}, error: ${e.message}")))
    case _                => super.onServerError(req, ex)
  }

  override def onProdServerError(req: RequestHeader, ex: UsefulException) = {
    onApiServerError.applyOrElse((req, ex), (super.onProdServerError _).tupled)
  }

  override def onDevServerError(req: RequestHeader, ex: UsefulException) = {
    onApiServerError.applyOrElse((req, ex), (super.onDevServerError _).tupled)
  }

  def onApiServerError: PartialFunction[(RequestHeader, UsefulException), Future[Result]] = {
    case (req, ex) if isFromApi(req) => Future.successful(InternalServerError(JsonMessage(ex.getMessage)))
  }

  def isFromApi(request: RequestHeader) = {
    val uri = request.uri
    val b1 = uri.startsWith("/api_internal")
    val b2 = uri.startsWith("/api_private")
    val b3 = uri.startsWith("/sockets")
    b1 || b2 || b3
  }
}