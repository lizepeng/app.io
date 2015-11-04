package models.sys

import com.datastax.driver.core.utils.UUIDs
import helpers._
import models._

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author zepeng.li@gmail.com
 */
trait SystemAccounts {
  self: AppDomainComponents =>

  def _systemAccount(named: CanonicalNamed)(
    implicit ec: ExecutionContext, _users: Users
  ): Future[User] = for {
    uid <- _users._sysConfig.getOrElseUpdate(
      named.basicName, "system_account", UUIDs.timeBased()
    )
    user <- _users.find(uid).recoverWith {
      case e: User.NotFound => _users.save(
        User(
          id = uid,
          name = Name(named.basicName),
          email = EmailAddress(s"${named.basicName}@$domain")
        )(_users._internalGroups)
      )
    }
  } yield user
}