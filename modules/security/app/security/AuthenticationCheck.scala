package security

import play.api.mvc._

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait AuthenticationCheck
  extends ActionRefiner[MaybeUserRequest, UserRequest] {

  /**
   * when access denied
   *
   * @param req
   * @return
   */
  def onUnauthorized(req: RequestHeader): Result = throw Unauthorized()

  override protected def refine[A](
    req: MaybeUserRequest[A]
  ): Future[Either[Result, UserRequest[A]]] = {
    Future.successful {
      req.maybeUser match {
        case None    => Left(onUnauthorized(req.inner))
        case Some(u) => Right(UserRequest[A](u, req.inner))
      }
    }
  }
}