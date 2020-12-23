package jp.co.tis.lerna.payment.application.readmodelupdater

import java.time.Duration

import akka.Done
import akka.actor.{
  Actor,
  ActorRef,
  ActorSystem,
  NoSerializationVerificationNeeded,
  OneForOneStrategy,
  PoisonPill,
  Props,
  Status,
  Terminated,
}
import akka.cluster.Cluster
import akka.cluster.singleton.{ ClusterSingletonManager, ClusterSingletonManagerSettings }
import akka.pattern.{ pipe, BackoffOpts, BackoffSupervisor }
import akka.stream.scaladsl.RunnableGraph
import akka.stream.{ KillSwitch, Materializer }
import jp.co.tis.lerna.payment.application.util.shutdown.{ NoNeedShutdown, ShutdownCompleted, TryGracefulShutdown }
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import kamon.{ Kamon, Tags }
import lerna.log.AppActorLogging
import lerna.util.lang.Equals._
import lerna.util.time.JavaDurationConverters

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

trait HasDomainEventTag {
  def domainEventTag: String
}

trait ReadModelUpdater extends HasDomainEventTag {
  def generateReadModelUpdaterStream(system: ActorSystem): Future[RunnableGraph[(KillSwitch, Future[Done])]]

  def metricTag: Tags

  implicit def tenant: AppTenant
}

class ReadModelUpdaterSupervisor(childProps: Props)(implicit tenant: AppTenant) // FIXME: 自作ではなく Akka Projection を使う
    extends Actor
    with AppActorLogging
    with JavaDurationConverters {
  import lerna.util.tenant.TenantComponentLogContext.logContext
  private val supervisorStrategyConfig =
    context.system.settings.config
      .getConfig("jp.co.tis.lerna.payment.application.read-model-updater-singleton.supervisor-strategy")
  val maxNrOfRetries: Int             = supervisorStrategyConfig.getInt("max-nr-of-retries")
  val withinTimeRange: FiniteDuration = supervisorStrategyConfig.getDuration("within-time-range").asScala
  val minBackoff: Duration            = supervisorStrategyConfig.getDuration("min-back-off")
  val maxBackoff: Duration            = supervisorStrategyConfig.getDuration("max-back-off")
  val randomFactor: Double            = supervisorStrategyConfig.getDouble("random-factor")
  override val supervisorStrategy: OneForOneStrategy = OneForOneStrategy(maxNrOfRetries, withinTimeRange) {
    super.supervisorStrategy.decider
  }

  logger.info("start SupervisorActor, childProps = {}", childProps)

  val backoffSupervisorProps: Props = BackoffSupervisor.props(
    BackoffOpts.onFailure(
      childProps = childProps,
      childName = "ReadModelUpdaterSingleton",
      minBackoff = minBackoff,
      maxBackoff = maxBackoff,
      randomFactor = randomFactor,
    ),
  )

  private val child = context.actorOf(backoffSupervisorProps, "BackoffSupervisor")

  context.watch(child)

  override def receive: Receive = {
    case Terminated(`child`) =>
      logger.error(s"自動的にリカバリできない問題で {} が停止しました。人手によるリカバリが必要です。", child)
      context.stop(self)

    case TryGracefulShutdown(address) if address === Cluster(context.system).selfAddress =>
      logger.info(s"{} の停止を準備します。", address)
      context.become(readyToLeaveCluster)
      child ! ReadModelUpdaterSingleton.End
      sender() ! ShutdownCompleted

    case TryGracefulShutdown(address) =>
      logger.info(s"{} のアドレスが一致しません。", address)
      sender() ! NoNeedShutdown

    case msg if sender() === child => context.parent ! msg
    case msg                       => child forward msg
  }

  def readyToLeaveCluster: Receive = {
    case Terminated(`child`) =>
      logger.info(s"子の {} が停止したので停止します", child)
      context.stop(self)
    case msg if sender() === child => context.parent ! msg
    case msg                       => child forward msg
  }
}

object ReadModelUpdaterSingleton {
  def props(readModelUpdater: ReadModelUpdater): Props = {
    import readModelUpdater.tenant
    val childProps = Props(new ReadModelUpdaterSingleton(readModelUpdater))
    Props(new ReadModelUpdaterSupervisor(childProps))
  }

  final case class ReadModelStreamStarted(killSwitch: KillSwitch) extends NoSerializationVerificationNeeded

  final case object End extends NoSerializationVerificationNeeded
  def actorName(readModelUpdater: ReadModelUpdater): String = {
    s"${readModelUpdater.tenant.id}-${readModelUpdater.domainEventTag}Aggregator" // TODO: name重複の可能性について調査
  }

  def startAsSingleton(system: ActorSystem, readModelUpdater: ReadModelUpdater): ActorRef = {
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = props(readModelUpdater),
        terminationMessage = End,
        settings = ClusterSingletonManagerSettings(system),
      ),
      name = actorName(readModelUpdater),
    )
  }
}

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class ReadModelUpdaterSingleton(readModelUpdater: ReadModelUpdater)
    extends Actor
    with AppActorLogging
    with JavaDurationConverters {
  import ReadModelUpdaterSingleton._
  import context.dispatcher
  import readModelUpdater.tenant
  import lerna.util.tenant.TenantComponentLogContext.logContext
  logger.info("ReadModelUpdaterSingleton start: {}", self.path)

  var maybeKillSwitch: Option[KillSwitch] = None

  override def receive: Receive = {
    case Done =>
      logger.info("complete readModelUpdaterStream Success")
    case Status.Failure(exception) =>
      logger.warn(exception, "complete readModelUpdaterStream Failure")
      throw exception
    case ReadModelStreamStarted(killSwitch) =>
      maybeKillSwitch = Option(killSwitch)
    case End =>
      maybeKillSwitch.foreach(_.shutdown())
      // context stop self すると stream 終了時の future pipeTo self が deadLetterになってしまいテストができないため遅らせる
      val stopSelfDelay = context.system.settings.config
        .getDuration("jp.co.tis.lerna.payment.application.read-model-updater-singleton.stop-self-delay").asScala
      context.system.scheduler.scheduleOnce(stopSelfDelay, self, PoisonPill)
  }

  implicit val materializer: Materializer = Materializer(context)

  readModelUpdater
    .generateReadModelUpdaterStream(context.system).onComplete {
      case Success(runnableGraph) =>
        val (killSwitch, streamFinishedSignal) = runnableGraph.run()
        self ! ReadModelStreamStarted(killSwitch)
        streamFinishedSignal pipeTo self
      case Failure(exception) =>
        self ! Status.Failure(new RuntimeException("ReadModelUpdaterStreamの作成に失敗しました", exception))
    }

  private[this] val singletonCounter = Kamon
    .gauge("payment-app.rmu.number_of_singleton")
    .refine(readModelUpdater.metricTag)

  override def preStart(): Unit = {
    super.preStart()
    singletonCounter.increment()
  }

  override def postStop(): Unit = {
    singletonCounter.decrement()
    super.postStop()
  }
}
