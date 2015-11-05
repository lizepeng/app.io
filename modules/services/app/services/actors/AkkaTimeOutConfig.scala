package services.actors

import akka.util.Timeout
import helpers.CanonicalNamed
import play.api.Configuration

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */

trait AkkaTimeOutConfig {
  self: CanonicalNamed =>

  def configuration: Configuration

  implicit lazy val timeout: Timeout = Timeout(
    configuration
      .getMilliseconds(s"$canonicalName.akka.timeout")
      .orElse(configuration.getMilliseconds(s"$packageName.akka.timeout"))
      .map(_ / 1000)
      .getOrElse(5L) seconds
  )
}