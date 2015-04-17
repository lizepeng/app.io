package models

import java.util.UUID

import com.datastax.driver.core.Row
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.Implicits._
import com.websudos.phantom.query.{AssignmentsQuery, SelectQuery, UpdateWhere}
import helpers.Logging
import models.cassandra._
import org.joda.time.DateTime

import scala.concurrent.Future
import scala.reflect.runtime.universe._

/**
 * @author zepeng.li@gmail.com
 */
sealed class SessionData
  extends CassandraTable[SessionData, UUID]
  with ExtCQL[SessionData, UUID]
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

object SessionData extends SessionData with Cassandra {

  def get[T: TypeTag](
    key: String
  )(
    implicit user: User
  ): Future[Option[T]] = {
    typeOf[T] match {
      case t if t =:= typeOf[DateTime] => get0(key, select(_.value_date))
      case t if t =:= typeOf[Int]      => get0(key, select(_.value_int))
      case _                           => Future.successful(None)
    }
  }.map(_.map(_.asInstanceOf[T]))

  def set[T: TypeTag](
    key: String, value: T
  )(
    implicit user: User
  ): Future[Boolean] = typeOf[T] match {
    case t if t =:= typeOf[DateTime] =>
      set0(key, _.modify(_.value_date setTo value.asInstanceOf[DateTime]))
    case t if t =:= typeOf[Int]      =>
      set0(key, _.modify(_.value_int setTo value.asInstanceOf[Int]))
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

  private def get0[T](
    key: String,
    select: SelectQuery[SessionData, T]
  )(
    implicit user: User
  ): Future[Option[T]] = CQL {
    select
      .where(_.user_id eqs user.id)
      .and(_.key eqs key)
  }.one()

  private def set0(
    key: String,
    modify: UpdateWhere[SessionData, UUID]
      => AssignmentsQuery[SessionData, UUID]
  )(
    implicit user: User
  ): Future[Boolean] = CQL {
    modify(
      update.ttl(86400)
        .where(_.user_id eqs user.id)
        .and(_.key eqs key)
    )
  }.future().map(_ => true)

}