package controllers.api

import elasticsearch._
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

  override val moduleName = "search"

  def index(types: Seq[String], q: Option[String], p: Pager) =
    PermCheck(_.Index).async { implicit req =>
      ES.Multi(
        _.Search(q, p / 2) in User,
        _.Search(q, p / 2) in Group
      ).future().map(ESMPage(p, _)).map { page =>
        Ok(page).withHeaders(
          linkHeader(page, routes.Users.index(Nil, q, _))
        )
      }
    }

  implicit class RichPager(val p: Pager) extends AnyVal {

    def /(n: Int) = p.copy(start = p.start / n, limit = p.limit / n)
  }

}
