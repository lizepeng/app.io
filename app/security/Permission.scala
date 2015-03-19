package security

import helpers.BaseException

/**
 * @author zepeng.li@gmail.com
 */
trait Permission[P, A, R] {
  def canAccess: Boolean = false

  def principal: P

  def action: A

  def resource: R
}

object Permission {

  abstract class Denied(module_name: String)
    extends BaseException(s"$module_name.permission.denied")

}