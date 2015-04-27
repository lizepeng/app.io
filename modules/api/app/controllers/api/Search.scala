package controllers.api

import elasticsearch.ES
import helpers._
import models._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import security.PermissionCheckable

/**
 * @author zepeng.li@gmail.com
 */
object Search
  extends Controller
  with ExHeaders with PermissionCheckable {

  def index(types: Seq[String], q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      
      (ES.Search(q, p) in User).map { page =>
        Ok(page).withHeaders(
          linkHeader(page, routes.Users.index(Nil, q, _))
        )
      }
    }
}