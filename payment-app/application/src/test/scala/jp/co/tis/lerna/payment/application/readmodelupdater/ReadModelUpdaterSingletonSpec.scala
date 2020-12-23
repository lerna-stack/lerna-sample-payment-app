package jp.co.tis.lerna.payment.application.readmodelupdater

import akka.Done
import akka.actor.{
  Actor,
  ActorKilledException,
  ActorSystem,
  Address,
  NoSerializationVerificationNeeded,
  PoisonPill,
  Props,
  Status,
}
import akka.cluster.Cluster
import akka.stream.scaladsl.{ Flow, Keep, RunnableGraph, Sink, Source }
import akka.stream.{ KillSwitch, KillSwitches }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.application.readmodelupdater.ReadModelUpdaterSingleton.End
import jp.co.tis.lerna.payment.application.util.shutdown.{ NoNeedShutdown, ShutdownCompleted, TryGracefulShutdown }
import jp.co.tis.lerna.payment.readmodel.{ JDBCSupport, ReadModelDIDesign }
import jp.co.tis.lerna.payment.utility.UtilityDIDesign
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant._
import kamon.Tags
import lerna.testkit.airframe.DISessionSupport
import lerna.util.lang.Equals._
import wvlet.airframe.Design

import scala.concurrent.Future

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "lerna.warts.Awaits",
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
class ReadModelUpdaterMock(implicit system: ActorSystem, val tenant: AppTenant) extends ReadModelUpdater {
  override def domainEventTag: String = "dummy"

  val source      = Source(1 to 10)
  var isFirstTime = true
  val flowUnderTest = Flow[Int].map { input =>
    if (input === 5 && isFirstTime) {
      isFirstTime = false
      throw new RuntimeException("dummy")
    }
    if (input === 6) {
      throw ActorKilledException("Test") // ReadModelUpdater停止時のログ確認のため。目視でチェック
    }
    input
  }
  val probe = TestProbe()
  case object ReadModelUpdatingSucceeded extends NoSerializationVerificationNeeded
  val sink = Sink.actorRef(probe.ref, ReadModelUpdatingSucceeded, Status.Failure)

  override def generateReadModelUpdaterStream(system: ActorSystem): Future[RunnableGraph[(KillSwitch, Future[Done])]] =
    Future.successful {
      source.viaMat(KillSwitches.single)(Keep.right).via(flowUnderTest).alsoTo(sink).toMat(Sink.ignore)(Keep.both)
    }

  override def metricTag: Tags = Map.empty[String, String]
}

final case class TestActor() extends Actor {
  override def receive: Receive = {
    case "1" =>
      sender() ! Done
    case "2" => context.parent ! Done
    case "3" => self ! PoisonPill
  }
}

// Lint回避のため
@SuppressWarnings(
  Array(
    "lerna.warts.Awaits",
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
class ReadModelUpdaterSingletonSpec
    extends TestKit(ActorSystem("ReadModelUpdaterSingletonSpec"))
    with StandardSpec
    with DISessionSupport
    with JDBCSupport
    with ImplicitSender {
  override protected val diDesign: Design = ReadModelDIDesign.readModelDesign
    .add(UtilityDIDesign.utilityDesign)
    .bind[Config].toInstance(ConfigFactory.load())
    .bind[ActorSystem].toInstance(system)
    .bind[ReadModelUpdater].to[ReadModelUpdaterMock]
    .bind[AppTenant].toInstance(Example)

  "ReadModelUpdaterSingletonSpec" when {
    val system           = diSession.build[ActorSystem]
    val readModelUpdater = diSession.build[ReadModelUpdater]
    ReadModelUpdaterSingleton.startAsSingleton(system, readModelUpdater)
    val actor = system.actorOf(ReadModelUpdaterSingleton.props(readModelUpdater))
    "Singletonとして起動" in {
      actor ! Done
      expectNoMessage()
    }
    "Singletonの停止" in {
      actor ! End
      expectNoMessage()
    }
    val supervisor = system.actorOf(Props(new ReadModelUpdaterSupervisor(Props(TestActor()))))
    "ReadModelUpdaterSupervisor receive Done" in {
      supervisor ! "1"
      expectMsg(Done)
    }

    "ReadModelUpdaterSupervisor receive parent" in {
      supervisor ! "2"
      expectNoMessage()
    }

    "TryGracefulShutdown(address not match)を受信" should {
      "NoNeedShutdownを返す" in {
        supervisor ! TryGracefulShutdown(Address("", ""))
        expectMsg(NoNeedShutdown)
      }
    }

    "TryGracefulShutdown(address match)を受信" should {
      "ShutdownCompleted" in {
        supervisor ! TryGracefulShutdown(Cluster(system).selfAddress)
        expectMsg(ShutdownCompleted)
      }
    }

    "ReadModelUpdaterSupervisor receive no message" in {
      supervisor ! "3"
      expectNoMessage()
    }

  }

  override def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }
}
