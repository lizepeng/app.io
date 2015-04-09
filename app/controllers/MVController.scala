package controllers

import helpers.ModuleLike
import play.api.mvc.Controller
import security.PermCheckable

/**
 * @author zepeng.li@gmail.com
 */
abstract class MVController[T <: ModuleLike](model: T)
  extends MVModule(model.moduleName)
  with Controller with PermCheckable