package security

import models.{AccessControls, Users}

/**
 * @author zepeng.li@gmail.com
 */
//case class PermCheckRequired(
//  _users: Users,
//  _accessControls: AccessControls
//)
//
//trait PermCheckComponents {
//
//  def _permCheckRequired: PermCheckRequired
//
//  implicit def _users: Users =
//    _permCheckRequired._users
//
//  implicit def _accessControls: AccessControls =
//    _permCheckRequired._accessControls
//}