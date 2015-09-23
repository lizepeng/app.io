package helpers

import play.api.Configuration

/**
 * @author zepeng.li@gmail.com
 */
trait AppConfigComponents extends AppDomainComponents {
  self: CanonicalNamed =>

  def configuration: Configuration

  def config: Configuration =
    configuration.getConfig(canonicalName).getOrElse(Configuration.empty)
}

trait AppDomainComponents {

  import AppDomainComponents._

  def configuration: Configuration

  def domain = getEssentialString("app.domain")

  /**
   *
   * @param key the configuration key
   * @return
   * @throws Required if corresponding value not exists
   */
  private def getEssentialString(key: String) =
    configuration.getString(key).getOrElse(throw new Required(key))
}

object AppDomainComponents {

  class Required(key: String)
    extends RuntimeException(s"<$key> is required")
}