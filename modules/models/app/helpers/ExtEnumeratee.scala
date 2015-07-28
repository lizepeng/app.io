package helpers

import play.api.libs.iteratee.Enumeratee.{CheckDone, MapM}
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

    /**
     * Like `take`, but take long type count.
     */
    def take[E](count: Long): PlayEnumeratee[E, E] = new CheckDone[E, E] {
      def step[A](remaining: Long)(k: K[E, A]): K[E, Iteratee[E, A]] = {
        case in@Input.El(_) if remaining == 1 => Done(k(in), Input.Empty)
        case in@Input.El(_) if remaining > 1  =>
          new CheckDone[E, E] {def continue[B](k: K[E, B]) = Cont(step(remaining - 1)(k))} &> k(in)
        case Input.Empty if remaining > 0     =>
          new CheckDone[E, E] {def continue[B](k: K[E, B]) = Cont(step(remaining)(k))} &> k(Input.Empty)
        case Input.EOF                        => Done(Cont(k), Input.EOF)
        case in                               => Done(Cont(k), in)
      }

      def continue[A](k: K[E, A]) = if (count <= 0) Done(Cont(k), Input.EOF) else Cont(step(count)(k))
    }
  }

  implicit def wrappedEnumeratee(e: Enumeratee.type): PlayEnumeratee.type = PlayEnumeratee
}