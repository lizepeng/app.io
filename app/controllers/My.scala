package controllers

import models.Group
import security._
import views._

/**
 * @author zepeng.li@gmail.com
 */
object My extends MVController(Group) {

  def dashboard =
    (MaybeUserAction >> AuthCheck) { implicit req =>
      Ok(html.my.dashboard())
    }
}