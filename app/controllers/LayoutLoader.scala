package controllers

import helpers._
import models._
import play.api.mvc._
import security._
import shapeless._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
case class LayoutLoader(
  implicit
  val basicPlayApi: BasicPlayApi,
  val _groups: Groups
)
  extends ActionTransformer[UserOptRequest, UserOptRequest]
  with BasicPlayComponents
  with UsersComponents
  with InternalGroupsComponents
  with DefaultPlayExecutor {

  val prefLens = lens[User].preferences

  def loadLayoutOf(user: User): Future[User] =
    _groups.findLayouts(user.groups).map { layouts =>
      val list = layouts.collect {
        case (gid, layout) if layout.isDefined => layout.get
      }
      prefLens.modify(user)(_ + ("layouts" -> list))
    }

  override protected def transform[A](
    request: UserOptRequest[A]
  ): Future[UserOptRequest[A]] = request match {
    case UserOptRequest(None, _)           => Future.successful(request)
    case UserOptRequest(Some(user), inner) => loadLayoutOf(user).map { u => UserOptRequest(Some(u), inner) }
  }

}