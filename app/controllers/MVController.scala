package controllers

import helpers.ModuleLike
import play.api.mvc.Controller
import security._

/**
 * @author zepeng.li@gmail.com
 */
abstract class MVController[T <: ModuleLike](model: T)
  extends MVModule(model.moduleName)
  with Controller with PermissionCheckable