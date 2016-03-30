package services.actors

import akka.actor._
import akka.cluster.sharding._
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
      entityProps = props,
      settings = ClusterShardingSettings(actorSystem),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId(numberOfShards)
    )
  }

  def getRegion(system: ActorSystem) = {
    ClusterSharding(system).shardRegion(shardName)
  }

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case env@Envelope(id, _) => (id.toString, env)
  }

  def extractShardId(numberOfShards: Int): ShardRegion.ExtractShardId = {
    case Envelope(id, _) => (math.abs(id.hashCode) % numberOfShards).toString
  }
}