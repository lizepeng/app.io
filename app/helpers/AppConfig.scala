package helpers

import play.api.{Application, Configuration}

/**
 * @author zepeng.li@gmail.com
 */
trait AppConfig {
  self: Logging =>

  def appConfig(implicit app: Application) = app.configuration

  def config(implicit app: Application): Configuration = appConfig.getConfig(module_name).getOrElse(Configuration.empty)
}