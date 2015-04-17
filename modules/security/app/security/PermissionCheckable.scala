package security

import helpers.ModuleLike

/**
 * @author zepeng.li@gmail.com
 */

trait PermissionCheckable extends ModuleLike {

  implicit lazy val CheckedModuleName = CheckedResource(fullModuleName)
}