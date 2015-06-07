/**
 * @author zepeng.li@gmail.com
 */

import play.api._
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router
import play.core.SourceMapper

import scala.concurrent._

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
    if (statusCode == play.api.http.Status.BAD_REQUEST) {
      if (request.uri.startsWith("/api")) {
        Future.successful(Results.NotFound)
      }
    }

    if (statusCode == play.api.http.Status.NOT_FOUND) {
      if (request.uri.startsWith("/api")) {
        Future.successful(Results.NotFound)
      }
    }

    super.onClientError(request, statusCode, message)
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