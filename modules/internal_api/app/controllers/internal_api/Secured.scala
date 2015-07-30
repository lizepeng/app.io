package controllers.internal_api

import helpers._
import security._

/**
 * @author zepeng.li@gmail.com
 */
abstract class Secured(override val basicName: String)
  extends CanonicalNamed
  with PermissionCheckable {

  def this(named: CanonicalNamed) { this(named.basicName) }
}