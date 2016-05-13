package security

import akka.stream._
import play.api.libs.streams._
import play.api.mvc._

import scala.concurrent._

/**
 * @author zepeng.li@gmail.com
 */
trait BodyParserFunction[-R, +P] {
  self =>

  def invoke[B](req: R, block: P => Future[BodyParser[B]]): Future[BodyParser[B]]

  def defaultContext: ExecutionContext

  def materializer: Materializer

  def andThen[Q](other: BodyParserFunction[P, Q]): BodyParserFunction[R, Q] =
    new BodyParserFunction[R, Q] {
      def invoke[B](req: R, block: Q => Future[BodyParser[B]]) =
        self.invoke[B](req, other.invoke[B](_, block))
      def defaultContext = self.defaultContext
      def materializer = self.materializer
    }
}

trait BodyParserBuilder[+R] extends BodyParserFunction[RequestHeader, R] {
  self =>

  final def async[B](block: R => Future[BodyParser[B]]) = new BodyParser[B] {
    def apply(rh: RequestHeader) = Accumulator.flatten {
      invoke(rh, block).map(_.apply(rh))(defaultContext)
    }(materializer)
  }

  override def andThen[Q](other: BodyParserFunction[R, Q]): BodyParserBuilder[Q] =
    new BodyParserBuilder[Q] {
      def invoke[B](rh: RequestHeader, block: Q => Future[BodyParser[B]]) =
        self.invoke[B](rh, other.invoke[B](_, block))
      def defaultContext = self.defaultContext
      def materializer = self.materializer
    }

  def >>[Q](other: BodyParserFunction[R, Q]): BodyParserBuilder[Q] = andThen(other)
}

trait BodyParserRefiner[-R, +P] extends BodyParserFunction[R, P] {

  protected def refine[B](req: R): Future[Either[BodyParser[B], P]]

  final def invoke[B](req: R, block: P => Future[BodyParser[B]]) =
    refine(req).flatMap(_.fold(Future.successful, block))(defaultContext)
}


trait BodyParserFilter[R] extends BodyParserRefiner[R, R] {

  protected def filter[B](req: R): Future[Option[BodyParser[B]]]

  final def refine[A](req: R) = filter(req).map(_.toLeft(req))(defaultContext)
}

trait BodyParserTransformer[-R, +P] extends BodyParserRefiner[R, P] {

  protected def transform(req: R): Future[P]

  final def refine[B](req: R) = transform(req).map(Right(_))(defaultContext)
}