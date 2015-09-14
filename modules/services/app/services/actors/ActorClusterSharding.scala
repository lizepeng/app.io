package services.actors

import akka.actor._
import akka.contrib.pattern.{ClusterSharding, ShardRegion}
import play.api.Configuration

import scala.language.postfixOps

/**
 * @author zepeng.li@gmail.com
 */
trait ActorClusterSharding {

  def shardName: String

  def props: Props

  def startRegion(
    configuration: Configuration,
    actorSystem: ActorSystem
  ) = {
    //Based on Akka Docs, the number of shards should be a factor
    //ten greater than the planned maximum number of cluster nodes.
    val numberOfShards = configuration
      .getInt("app.akka.cluster.number_of_nodes")
      .getOrElse(100) * 12

    ClusterSharding(actorSystem).start(
      typeName = shardName,
      entryProps = Some(props),
      idExtractor = idExtractor,
      shardResolver = shardResolver(numberOfShards)
    )
  }

  def getRegion(system: ActorSystem) = {
    ClusterSharding(system).shardRegion(shardName)
  }

  val idExtractor: ShardRegion.IdExtractor = {
    case env@Envelope(id, _) => (id.toString, env)
  }

  def shardResolver(numberOfShards: Int): ShardRegion.ShardResolver = {
    case Envelope(id, _) => (math.abs(id.hashCode) % numberOfShards).toString
  }
}