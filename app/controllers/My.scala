package controllers

import models.Group
import security._
import views._

/**
 * @author zepeng.li@gmail.com
 */
object My extends MVController(Group) {

  def dashboard =
    (UserAction >> AuthCheck) { implicit req =>
      Ok(html.my.dashboard())
    }
}