package security

import helpers.CanonicalNamed
import security.ModulesAccessControl._

/**
 * @author zepeng.li@gmail.com
 */
trait CheckedModuleName {
  self: CanonicalNamed =>

  implicit def checkedModuleName = CheckedModule(canonicalName)
}

trait PermissionCheckable extends CheckedModuleName {
  self: CanonicalNamed =>

  def AccessDef: BasicAccessDef
}