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

  implicit lazy val errorHandler = new UserActionExceptionHandler with DefaultExceptionHandler {
    override def onUnauthorized = _ => HttpBasicAuth.onUnauthorized("ping")
  }

  def ping = (MaybeUser(AuthenticateByAccessToken).Action() >> AuthChecker()) { req =>
    Ok(
      Json.prettyPrint(
        Json.obj(
          "username" -> req.user.user_name,
          "email" -> req.user.email,
          "datetime" -> DateTime.now
        )
      )
    )
  }
}