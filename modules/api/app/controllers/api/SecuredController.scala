package controllers.api

import helpers.ModuleLike
import play.api.mvc.Controller
import security._

/**
 * @author zepeng.li@gmail.com
 */
abstract class SecuredController(model: ModuleLike)
  extends Controller with PermissionCheckable {

  override val moduleName = model.moduleName
}