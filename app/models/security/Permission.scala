package models.security

/**
 * @author zepeng.li@gmail.com
 */
trait Permission[P, A, R] {
  type Checker = (P, A, R) => Boolean

  def check(checker: Checker): Boolean =
    checker(principal, action, resource)

  def principal: P

  def action: A

  def resource: R

}