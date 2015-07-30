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
  def onUnauthenticated(req: RequestHeader): Result = throw Unauthenticated()

  override protected def refine[A](
    req: MaybeUserRequest[A]
  ): Future[Either[Result, UserRequest[A]]] = {
    Future.successful {
      req.maybeUser match {
        case None    => Left(onUnauthenticated(req.inner))
        case Some(u) => Right(UserRequest[A](u, req.inner))
      }
    }
  }
}