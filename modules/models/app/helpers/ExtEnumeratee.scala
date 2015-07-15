package helpers

import play.api.libs.iteratee.Enumeratee.MapM
import play.api.libs.iteratee.Execution.{trampoline => dec}
import play.api.libs.iteratee.{Enumeratee => PlayEnumeratee, _}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
object ExtEnumeratee {

  object Enumeratee {

    def flattenOption[A](
      implicit ec: ExecutionContext
    ): PlayEnumeratee[Option[A], A] =
      PlayEnumeratee.filter[Option[A]](_.isDefined) ><>
        PlayEnumeratee.map(_.get)

    /**
     * Like `mapM`, but push EOF into stream when exception occurs.
     */
    def mapM1[E] = new MapM[E] {
      def apply[NE](f: E => Future[NE])(
        implicit ec: ExecutionContext
      ): PlayEnumeratee[E, NE] = PlayEnumeratee.mapInputM[E] {
        case Input.Empty => Future.successful(Input.Empty)
        case Input.EOF   => Future.successful(Input.EOF)
        case Input.El(e) => f(e).map(Input.El(_))(dec).recover { case e: Exception => Input.EOF }(dec)
      }(ec)
    }
  }

  implicit def wrappedEnumeratee(e: Enumeratee.type): PlayEnumeratee.type = PlayEnumeratee
}