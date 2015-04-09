package security

import helpers.{BaseException, Loggable}

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

  abstract class Denied[P, A, R](super_key: String)
    extends BaseException(s"$super_key.perm.denied")
    with Permission[P, A, R]

  abstract class Undefined[P, A, R](super_key: String)
    extends BaseException(s"$super_key.perm.undefined")
    with Permission[P, A, R]

  abstract class Granted[P, A, R](super_key: String)
    extends Loggable with Permission[P, A, R] {
    val code = s"$super_key.perm.granted"

    override def canAccess = true
  }

}