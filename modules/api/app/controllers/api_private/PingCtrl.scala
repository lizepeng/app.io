package controllers.api_private

import helpers.BasicPlayApi
import models.Users
import org.joda.time.DateTime
import play.api.libs.json.Json
import play.api.mvc._
import security._

/**
 * @author zepeng.li@gmail.com
 */
class PingCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _users: Users
) extends Controller {

  val action: ActionBuilder[UserRequest] = MaybeUser(AuthenticateByAccessToken).Action() >>
    new AuthenticationCheck {
      override def onUnauthorized(req: RequestHeader): Result = HttpBasicAuth.onUnauthorized("ping")
    }

  def ping = action { req =>
    Ok(
      Json.prettyPrint(
        Json.obj(
          "username" -> req.user.name,
          "email" -> req.user.email,
          "datetime" -> DateTime.now
        )
      )
    )
  }
}