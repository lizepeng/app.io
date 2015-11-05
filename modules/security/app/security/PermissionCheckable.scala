package security

import helpers.CanonicalNamed
import security.ModulesAccessControl._

/**
 * @author zepeng.li@gmail.com
 */

trait PermissionCheckable {
  self: CanonicalNamed =>

  implicit lazy val CheckedModuleName = CheckedModule(canonicalName)
}