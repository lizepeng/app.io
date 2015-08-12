package controllers.api_internal

import helpers._
import play.api.mvc._
import services.web.ip_api.IPService

/**
 * @author zepeng.li@gmail.com
 */
class IPCtrl(
  implicit
  val basicPlayApi: BasicPlayApi,
  val ipService: IPService
)
  extends Controller
  with BasicPlayComponents
  with DefaultPlayExecutor {

  def show(ip: String) = Action.async { req =>
    ipService.find(ip).map { reps => Ok(reps) }
  }
}