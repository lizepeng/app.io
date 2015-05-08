package helpers

import play.api.libs.iteratee.Enumeratee.MapM
import play.api.libs.iteratee.Execution.Implicits.{defaultExecutionContext => dec}
import play.api.libs.iteratee.{Enumeratee, Input}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/**
 * @author zepeng.li@gmail.com
 */
object ExtEnumeratee {

  play.api.libs.iteratee.Execution.trampoline

  def flattenOption[A]: Enumeratee[Option[A], A] =
    Enumeratee.filter[Option[A]](_.isDefined) ><> Enumeratee.map(_.get)

  /**
   * Like `mapM`, but push EOF into stream when exception occurs.
   */
  def mapM1[E] = new MapM[E] {
    def apply[NE](f: E => Future[NE])(
      implicit ec: ExecutionContext
    ): Enumeratee[E, NE] = Enumeratee.mapInputM[E] {
      case Input.Empty => Future.successful(Input.Empty)
      case Input.EOF   => Future.successful(Input.EOF)
      case Input.El(e) => f(e).map(Input.El(_))(dec).recover { case e: Exception => Input.EOF }(dec)
    }(ec)
  }
}