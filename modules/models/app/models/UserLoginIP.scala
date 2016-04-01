package models

import java.net.InetAddress

import com.datastax.driver.core.Row
import com.websudos.phantom.dsl._
import helpers._
import models.cassandra._
import org.joda.time.DateTime

import scala.concurrent.Future

/**
 * @author zepeng.li@gmail.com
 */
trait UserLoginIPCanonicalNamed extends CanonicalNamed {

  override val basicName = "user_login_ip"
}

sealed class UserLoginIPTable
  extends NamedCassandraTable[UserLoginIPTable, (DateTime, InetAddress)]
  with UserLoginIPCanonicalNamed {

  object user_id
    extends UUIDColumn(this)
    with PartitionKey[UUID]

  object datetime
    extends DateTimeColumn(this)
    with PrimaryKey[DateTime]

  object ip
    extends InetAddressColumn(this)

  override def fromRow(r: Row): (DateTime, InetAddress) = (datetime(r), ip(r))
}

object UserLoginIPs
  extends UserLoginIPCanonicalNamed

class UserLoginIPs(
  implicit
  val basicPlayApi: BasicPlayApi,
  val keySpaceDef: KeySpaceDef
)
  extends UserLoginIPTable
  with ExtCQL[UserLoginIPTable, (DateTime, InetAddress)]
  with BasicPlayComponents
  with CassandraComponents
  with BootingProcess
  with Logging {

  onStart(CQL(create.ifNotExists).future())

  def log(
    user_id: UUID,
    ip: InetAddress
  ): Future[ResultSet] = CQL {
    update
      .where(_.user_id eqs user_id)
      .and(_.datetime eqs DateTime.now)
      .modify(_.ip setTo ip)
  }.future()
}