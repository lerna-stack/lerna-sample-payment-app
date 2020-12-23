package jp.co.tis.lerna.payment.application.readmodelupdater

import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.{ ActorSystem, Address, PoisonPill }
import akka.cluster.singleton.{ ClusterSingletonProxy, ClusterSingletonProxySettings }
import akka.pattern.ask
import akka.util.Timeout
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.detail.ECHouseMoneySalesDetailReadModelUpdater
import jp.co.tis.lerna.payment.application.util.shutdown.TryGracefulShutdown
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import wvlet.airframe._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait ReadModelUpdaterSingletonManager {
  protected val system: ActorSystem
  protected def readModelUpdaters: Seq[ReadModelUpdater]

  /** ReadModelUpdater(Singleton) を起動する
    * Singleton は Cluster内のどこかに起動し、その中で ReadModelUpdater(Stream)が run する
    * すでに起動している状態で呼び出してもOK
    */
  def startReadModelUpdaters(): Unit = {
    readModelUpdaters.foreach { readModelUpdater =>
      ReadModelUpdaterSingleton.startAsSingleton(system, readModelUpdater)
    }
  }

  /** ReadModelUpdater(Singleton) が targetNode で起動していたら ReadModelUpdater を停止する
    * @param targetNode 停止対象Node (graceful shutdown のためには Cluster#selfAddress を渡す)
    * @return 全 ReadModelUpdater が停止要求を受け付けたら Success になる Future （※ 停止完了ではない）
    */
  def stopReadModelUpdatersIfActiveIn(targetNode: Address): Future[Done.type] = {
    import system.dispatcher

    val futures = readModelUpdaters.map(rmu => shutdownRMU(rmu, targetNode))

    Future.sequence(futures).map(_ => Done)
  }

  private def shutdownRMU(rmu: ReadModelUpdater, targetNode: Address): Future[Any] = {
    val proxy = system.actorOf(
      ClusterSingletonProxy
        .props(
          singletonManagerPath = s"/user/${ReadModelUpdaterSingleton.actorName(rmu)}",
          settings = ClusterSingletonProxySettings(system),
        ),
    )

    implicit val timeout: Timeout = Timeout(FiniteDuration(5, TimeUnit.SECONDS))
    import system.dispatcher
    val future = proxy ? TryGracefulShutdown(targetNode)
    future.onComplete(_ => proxy ! PoisonPill)
    future
  }
}

class ReadModelUpdaterSingletonManagerImpl(
    val system: ActorSystem,
    session: Session,
) extends ReadModelUpdaterSingletonManager {
  @SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
  private val allReadModelUpdaters: Seq[ReadModelUpdater] =
    AppTenant.values
      .flatMap { tenant =>
        val childDesign  = newDesign.bind[AppTenant].toInstance(tenant)
        val childSession = session.newChildSession(childDesign)
        Seq(
          childSession.build[ECHouseMoneySalesDetailReadModelUpdater],
        )
      }

  override def readModelUpdaters: Seq[ReadModelUpdater] = allReadModelUpdaters
}
