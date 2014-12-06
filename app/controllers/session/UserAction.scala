package controllers.session

import controllers._
import play.api.mvc._

/**
 * @author zepeng.li@gmail.com
 */
object UserAction
  extends ActionBuilder[UserRequest]
  with ActionTransformer[Request, UserRequest]
  with Session