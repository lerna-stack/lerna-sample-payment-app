package jp.co.tis.lerna.payment.application.util.shutdown

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.cluster.Cluster
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings, ShardRegion }
import akka.testkit.TestKit
import jp.co.tis.lerna.payment.adapter.util.shutdown.GracefulShutdownApplication
import jp.co.tis.lerna.payment.application.readmodelupdater.{ ReadModelUpdater, ReadModelUpdaterSingletonManager }
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import lerna.testkit.airframe.DISessionSupport
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Millis, Seconds, Span }
import wvlet.airframe.{ newDesign, Design }

object GracefulShutdownApplicationSpec {
  private def startClusterSharding()(implicit system: ActorSystem): ActorRef = {

    val extractEntityId: ShardRegion.ExtractEntityId = {
      case msg => ("dummy-entity-id", msg)
    }

    val extractShardId: ShardRegion.ExtractShardId = { _ =>
      "dummy-shard-id"
    }

    ClusterSharding(system).start(
      typeName = "dummy-type-name",
      entityProps = Props(new Actor() {
        override def receive: Receive = Actor.emptyBehavior
      }),
      settings = ClusterShardingSettings(system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId,
    )
  }

  private class ReadModelUpdaterSingletonManagerMock(
      val system: ActorSystem,
  ) extends ReadModelUpdaterSingletonManager {
    override protected def readModelUpdaters: Seq[ReadModelUpdater] = ???
  }
}

// Lint回避のため
@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
  ),
)
class GracefulShutdownApplicationSpec
    extends TestKit(ActorSystem("GracefulShutdownApplicationSpec"))
    with StandardSpec
    with ScalaFutures
    with Eventually
    with DISessionSupport {

  import GracefulShutdownApplicationSpec._

  private val cluster = Cluster(system)
  cluster.join(cluster.selfAddress)

  override val diDesign: Design = newDesign
    .bind[ActorSystem].toInstance(system)
    .bind[GracefulShutdownApplication].to[GracefulShutdownApplicationImpl]
    .bind[ReadModelUpdaterSingletonManager].to[ReadModelUpdaterSingletonManagerMock]

  private val gracefulShutdownApplication = diSession.build[GracefulShutdownApplication]

  "requestGracefulShutdownShardRegion()" should {

    "ShardRegionを停止する" in {
      val shardRegion = startClusterSharding()
      watch(shardRegion)

      gracefulShutdownApplication.requestGracefulShutdownShardRegion()

      expectTerminated(shardRegion)
    }
  }

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(200, Millis)))

  override def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }
}
