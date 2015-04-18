package helpers

import helpers.AppConfig.Required
import play.api.{Application, Configuration}

/**
 * @author zepeng.li@gmail.com
 */
trait AppConfig {
  self: ModuleLike =>

  def appConfig(implicit app: Application) = app.configuration

  def config(implicit app: Application): Configuration =
    appConfig.getConfig(fullModuleName).getOrElse(Configuration.empty)

  def domain(implicit app: Application) = getEssentialString("app.domain")

  def hostname(implicit app: Application) = getEssentialString("app.hostname")

  /**
   *
   * @param key the configuration key
   * @return
   * @throws Required if corresponding value not exists
   */
  private def getEssentialString(key: String)(implicit app: Application) = {
    appConfig.getString(key).getOrElse(throw new Required(key))
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
   * @param app application instance
   * @return defined fetch-size or default value
   */
  def fetchSize(key: String = "")(implicit app: Application) =
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
   * @param app application instance
   * @return defined fetch-size or 0
   */
  def defaultFetchSize(implicit app: Application) =
    appConfig.getInt("cassandra.fetch-size").getOrElse(0)
}

object AppConfig {

  class Required(key: String)
    extends RuntimeException(s"<$key> is required")

}