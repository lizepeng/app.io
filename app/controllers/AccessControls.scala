package controllers

import controllers.api.SecuredController
import helpers._
import models._
import views._

/**
 * @author zepeng.li@gmail.com
 */
object AccessControls
  extends SecuredController(AccessControl)
  with ViewMessages {

  def index(pager: Pager) =
    PermCheck(_.Index).apply { implicit req =>
      Ok(html.access_controls.index(pager))
    }

}