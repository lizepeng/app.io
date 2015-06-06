package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import org.joda.time.DateTime

import scala.concurrent.Future
import scala.reflect.runtime.universe._

/**
 * @author zepeng.li@gmail.com
 */
sealed class SessionDataTable
  extends CassandraTable[SessionDataTable, UUID]
  with Logging {

  override val tableName = "session_data"

  object user_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object key
    extends StringColumn(this)
    with PrimaryKey[String]

  object value_date
    extends DateTimeColumn(this)

  object value_int
    extends IntColumn(this)

  override def fromRow(r: Row): UUID = user_id(r)
}

object SessionData extends SessionDataTable

class SessionData(
  implicit val basicPlayApi: BasicPlayApi
)
  extends SessionDataTable
  with ExtCQL[SessionDataTable, UUID]
  with BasicPlayComponents
  with Cassandra {

  def get[T: TypeTag](
    key: String
  )(
    implicit user: User
  ): Future[Option[T]] = {
    typeOf[T] match {
      case t if t =:= typeOf[DateTime] =>
        select(_.value_date)
          .where(_.user_id eqs user.id)
          .and(_.key eqs key)
          .one()
      case t if t =:= typeOf[Int]      =>
        select(_.value_int)
          .where(_.user_id eqs user.id)
          .and(_.key eqs key)
          .one()
      case _                           => Future.successful(None)
    }
  }.map(_.map(_.asInstanceOf[T]))

  def set[T: TypeTag](
    key: String, value: T
  )(
    implicit user: User
  ): Future[Boolean] = typeOf[T] match {
    case t if t =:= typeOf[DateTime] =>
      update.where(_.user_id eqs user.id)
        .and(_.key eqs key)
        .modify(_.value_date setTo value.asInstanceOf[DateTime])
        .future().map(_ => true)
    case t if t =:= typeOf[Int]      =>
      update.where(_.user_id eqs user.id)
        .and(_.key eqs key)
        .modify(_.value_int setTo value.asInstanceOf[Int])
        .future().map(_ => true)
    case _                           => Future.successful(false)
  }

  def remove(key: String)(
    implicit user: User
  ): Future[ResultSet] = CQL {
    delete
      .where(_.user_id eqs user.id)
      .and(_.key eqs key)
  }.future()

  def getOrElse[T: TypeTag](
    key: String, expiration: Int = 0
  )(
    orElse: => T
  )(
    implicit user: User
  ): Future[T] = {
    get[T](key).map {
      _.getOrElse {
        val value = orElse
        set(key, value)
        value
      }
    }
  }
}