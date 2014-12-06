package models

import com.websudos.phantom.zookeeper.SimpleCassandraConnector

/**
 * @author zepeng.li@gmail.com
 */
trait CassandraConnector extends SimpleCassandraConnector {
  val keySpace = "fact"
}

//
//trait ZooKeeperConnector extends DefaultZookeeperConnector {
//  val keySpace = "phantom_zookeeper_example"
//}