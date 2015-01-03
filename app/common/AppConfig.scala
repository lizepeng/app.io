package common

import play.api.{Application, Configuration}

/**
 * @author zepeng.li@gmail.com
 */
trait AppConfig {
  def config_key: String

  def appConfig(implicit app: Application) = app.configuration

  def config(implicit app: Application): Configuration = appConfig.getConfig(config_key).getOrElse(Configuration.empty)
}