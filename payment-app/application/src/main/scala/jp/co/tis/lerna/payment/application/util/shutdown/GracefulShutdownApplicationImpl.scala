package jp.co.tis.lerna.payment.application.util.shutdown

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.cluster.sharding.{ ClusterSharding, ShardRegion }
import jp.co.tis.lerna.payment.adapter.util.shutdown.GracefulShutdownApplication
import jp.co.tis.lerna.payment.application.readmodelupdater.ReadModelUpdaterSingletonManager
import lerna.log.AppLogging

import scala.concurrent.Future

class GracefulShutdownApplicationImpl(
    system: ActorSystem,
    readModelUpdaterSingletonManager: ReadModelUpdaterSingletonManager,
) extends GracefulShutdownApplication
    with AppLogging {
  import lerna.log.SystemComponentLogContext.logContext

  override def requestGracefulShutdownShardRegion(): Unit = {
    val clusterSharding = ClusterSharding(system)

    val shardRegionTypeNameSet = clusterSharding.shardTypeNames

    logger.info(s"ShardRegion の GracefulShutdown を開始します(対象: $shardRegionTypeNameSet)")

    for (typeName <- shardRegionTypeNameSet) {
      val shardRegion = clusterSharding.shardRegion(typeName)
      shardRegion ! ShardRegion.GracefulShutdown
    }
  }

  override def requestShutdownReadyReadModelUpdaterSupervisor(): Future[Any] = {
    val cluster     = Cluster(system)
    val selfAddress = cluster.selfAddress

    logger.info(s"ReadModelUpdaterSupervisor のShutdownの準備を開始します。(対象: $selfAddress)")

    readModelUpdaterSingletonManager.stopReadModelUpdatersIfActiveIn(selfAddress)
  }
}
