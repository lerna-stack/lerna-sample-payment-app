package jp.co.tis.lerna.payment.application.util.shutdown

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.Cluster
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import com.typesafe.config.ConfigFactory
import jp.co.tis.lerna.payment.adapter.util.shutdown.GracefulShutdownApplication
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import lerna.testkit.airframe.DISessionSupport
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.{ Millis, Seconds, Span }
import wvlet.airframe.{ newDesign, Design }

object GracefulShutdownApplicationSpec {
  private def startClusterSharding()(implicit system: ActorSystem[Nothing]): ActorRef[ShardingEnvelope[Unit]] = {
    ClusterSharding(system).init(
      Entity(EntityTypeKey[Unit]("dummy-type-name"))(createBehavior = _ => Behaviors.empty),
    )
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
    extends ScalaTestWithTypedActorTestKit(ConfigFactory.load("application.conf"))
    with StandardSpec
    with ScalaFutures
    with Eventually
    with DISessionSupport {

  import GracefulShutdownApplicationSpec._

  private val cluster = Cluster(system)
  cluster.join(cluster.selfAddress)

  override val diDesign: Design = newDesign
    .bind[ActorSystem[Nothing]].toInstance(system)
    .bind[GracefulShutdownApplication].to[GracefulShutdownApplicationImpl]

  private val gracefulShutdownApplication = diSession.build[GracefulShutdownApplication]

  "requestGracefulShutdownShardRegion()" should {

    "ShardRegionを停止する" in {
      val probe       = akka.testkit.TestProbe()(system.classicSystem)
      val shardRegion = startClusterSharding()
      probe.watch(shardRegion.toClassic)

      gracefulShutdownApplication.requestGracefulShutdownShardRegion()

      probe.expectTerminated(shardRegion.toClassic)
    }
  }

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(200, Millis)))
}
