package security

import helpers.ModuleLike

/**
 * @author zepeng.li@gmail.com
 */

trait PermCheckable extends ModuleLike {

  implicit lazy val CheckedModuleName = CheckedResource(fullModuleName)

  def CheckedActions: Seq[CheckedAction] = CommonActions
}
