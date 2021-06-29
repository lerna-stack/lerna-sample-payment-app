package jp.co.tis.lerna.payment.application.util.shutdown

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.{ ClusterSharding, ShardRegion }
import jp.co.tis.lerna.payment.adapter.util.shutdown.GracefulShutdownApplication
import lerna.log.AppLogging

class GracefulShutdownApplicationImpl(
    system: ActorSystem[Nothing],
) extends GracefulShutdownApplication
    with AppLogging {
  import lerna.log.SystemComponentLogContext.logContext

  override def requestGracefulShutdownShardRegion(): Unit = {
    val clusterSharding = ClusterSharding(system)

    val shardRegionTypeNameSet = clusterSharding.shardTypeNames

    logger.info(s"ShardRegion の GracefulShutdown を開始します(対象: ${shardRegionTypeNameSet.toString})")

    for (typeName <- shardRegionTypeNameSet) {
      val shardRegion = clusterSharding.shardRegion(typeName)
      shardRegion ! ShardRegion.GracefulShutdown
    }
  }
}
