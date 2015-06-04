package helpers

import helpers.AppConfig.Required
import play.api.Configuration

/**
 * @author zepeng.li@gmail.com
 */
trait AppConfig {
  self: CanonicalNamed =>

  def config(implicit configuration: Configuration): Configuration =
    configuration.getConfig(canonicalName)
      .getOrElse(Configuration.empty)

  def domain(implicit configuration: Configuration) =
    getEssentialString("app.domain")

  def hostname(implicit configuration: Configuration) =
    getEssentialString("app.hostname")

  /**
   *
   * @param key the configuration key
   * @return
   * @throws Required if corresponding value not exists
   */
  private def getEssentialString(key: String)(
    implicit configuration: Configuration
  ) = {
    configuration.getString(key).getOrElse(throw new Required(key))
  }

  /**
   * Return fetch size for Cassandra
   *
   * Use value declared by module respectively prior to reading default.
   *
   * For example
   * {{{
   *   module_name.fetch-size=3000
   * }}}
   *
   * @param configuration injected Configuration
   * @return defined fetch-size or default value
   */
  def fetchSize(key: String = "")(
    implicit configuration: Configuration
  ) =
    config.getInt(
      if (key.isEmpty) "fetch-size"
      else s"$key-fetch-size"
    ).getOrElse(defaultFetchSize)

  /**
   * Return fetch size for Cassandra
   *
   * If nothing defined in application.conf return 0,
   * and that means cassandra will take default fetch size.
   *
   * For example
   * {{{
   *   cassandra.fetch-size=2000
   * }}}
   * @param configuration injected Configuration
   * @return defined fetch-size or 0
   */
  def defaultFetchSize(implicit configuration: Configuration) =
    configuration.getInt("cassandra.fetch-size").getOrElse(0)
}

object AppConfig {

  class Required(key: String)
    extends RuntimeException(s"<$key> is required")

}