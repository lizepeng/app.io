package models.cassandra

import com.datastax.driver.core.Session
import com.websudos.phantom.connectors._

/**
 * @author zepeng.li@gmail.com
 */
trait CassandraComponents {

  implicit val keySpace: KeySpace = KeySpace("app")

  def cassandraManager: CassandraManager

  implicit val session: Session = {
    cassandraManager.initIfNotInited(keySpace.name)
    cassandraManager.session
  }
}