package security

import helpers.CanonicalNamed

/**
 * @author zepeng.li@gmail.com
 */

trait PermissionCheckable {
  self: CanonicalNamed =>

  implicit lazy val CheckedModuleName = CheckedResource(canonicalName)
}