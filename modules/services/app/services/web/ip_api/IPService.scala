package services.web.ip_api

import helpers._
import play.api.libs.json.JsValue
import play.api.libs.ws._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
class IPService(
  val basicPlayApi: BasicPlayApi,
  val wsClient: WSClient
)
  extends BasicPlayComponents
  with DefaultPlayExecutor {

  def find(ip: String = ""): Future[JsValue] = {
    wsClient.url(s"http://ip-api.com/json/$ip").get().map(_.json)
  }
}