package common

import play.api.Application

/**
 * @author zepeng.li@gmail.com
 */
trait AppConfig {
  def config(implicit app: Application) = app.configuration
}