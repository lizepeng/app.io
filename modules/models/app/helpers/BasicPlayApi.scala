package helpers

import play.api.Configuration
import play.api.i18n.{Langs, MessagesApi}

/**
 * @author zepeng.li@gmail.com
 */
case class BasicPlayApi(
  langs: Langs,
  messagesApi: MessagesApi,
  configuration: Configuration
)

trait BasicPlayComponents {

  def _basicPlayApi: BasicPlayApi

  implicit def messagesApi: MessagesApi = _basicPlayApi.messagesApi

  implicit def langs: Langs = _basicPlayApi.langs

  implicit def configuration: Configuration = _basicPlayApi.configuration
}