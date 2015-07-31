package protocols

import play.api.i18n.Messages
import play.api.libs.json.JsObject
import play.api.mvc._
import JsonProtocol.WrongTypeOfJson
import security.UserRequest

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
object BodyIsJsObject {

  def apply(f: JsObject => Future[Result])(
    implicit req: UserRequest[AnyContent], messages: Messages
  ): Future[Result] = {
    req.body.asJson match {
      case Some(obj: JsObject) => f(obj)
      case _                   =>
        Future.successful(Results.BadRequest(WrongTypeOfJson()))
    }
  }
}