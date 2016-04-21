import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router
import play.core.SourceMapper

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
class ErrorHandler(
  env: Environment,
  config: Configuration,
  sourceMapper: Option[SourceMapper],
  router: => Option[Router]
) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {

  override def onClientError(
    request: RequestHeader,
    statusCode: Int,
    message: String
  ) = {
    import play.api.http.Status._
    import play.api.mvc.Results._

    val uri = request.uri
    val b1 = uri.startsWith("/api_internal")
    val b2 = uri.startsWith("/api_private")
    val b3 = uri.startsWith("/sockets")

    if (env.mode == Mode.Prod && (b1 || b2 || b3)) Future.successful {
      statusCode match {
        case BAD_REQUEST => BadRequest
        case FORBIDDEN   => Forbidden
        case _           => NotFound
      }
    }
    else super.onClientError(request, statusCode, message)
  }

  override def onProdServerError(
    request: RequestHeader,
    exception: UsefulException
  ) = {
    Future.successful(
      InternalServerError("A server error occurred: " + exception.getMessage)
    )
  }

  override def onForbidden(request: RequestHeader, message: String) = {
    Future.successful(
      Forbidden("You're not allowed to access this resource.")
    )
  }
}