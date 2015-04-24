package controllers

import controllers.api.SecuredController
import models.Group
import security._
import views._

/**
 * @author zepeng.li@gmail.com
 */
object My extends SecuredController(Group) {

  def dashboard =
    (MaybeUserAction >> AuthCheck) { implicit req =>
      Ok(html.my.dashboard())
    }
}