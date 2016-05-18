package security

import helpers._
import play.api.mvc._

import scala.concurrent._
import scala.language.higherKinds

/**
 * @author zepeng.li@gmail.com
 */
trait ActionComponents {

  case class EmptyActionFunction[P[_]]()
    extends ActionFunction[P, P] {

    def invokeBlock[A](
      request: P[A], block: (P[A]) => Future[Result]
    ): Future[Result] = block(request)
  }

  case class EmptyBodyParserFunction[P](
    implicit val basicPlayApi: BasicPlayApi
  ) extends BodyParserFunction[P, P]
    with BodyParserFunctionComponents {

    def invoke[B](
      request: P, block: (P) => Future[BodyParser[B]]
    ): Future[BodyParser[B]] = block(request)
  }
}