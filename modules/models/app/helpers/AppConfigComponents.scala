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

trait AppDomainComponents extends EssentialConfig {

  def configuration: Configuration

  def domain = getEssentialString("app.domain")
}

trait EssentialConfig {

  def configuration: Configuration

  /**
   * Get String from configuration, which must be set
   *
   * @param key the configuration key
   * @return
   */
  def getEssentialString(key: String): String = {
    configuration.getString(key).getOrElse(
      throw configuration.reportError(key, s"Configuration Missing $key")
    )
  }

  /**
   * Get Int from configuration, which must be set
   *
   * @param key the configuration key
   * @return
   */
  def getEssentialInt(key: String): Int = {
    configuration.getInt(key).getOrElse(
      throw configuration.reportError(key, s"Configuration Missing $key")
    )
  }
}