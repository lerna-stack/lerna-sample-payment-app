package jp.co.tis.lerna.payment.application.readmodelupdater

import akka.actor.{ typed, ActorSystem }
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.slick.SlickProjection
import akka.projection.{ HandlerRecoveryStrategy, ProjectionBehavior, ProjectionId }
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.SalesDetailEventHandler
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.eventhandler.ECPaymentIssuingServiceEventHandler
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.model.SalesDetailDomainEvent
import jp.co.tis.lerna.payment.readmodel.JDBCService
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import wvlet.airframe._

class ReadModelUpdaterSingletonManager(
    classicSystem: ActorSystem,
    session: Session,
    jdbcService: JDBCService,
) {
  import akka.actor.typed.scaladsl.adapter._

  private implicit val system: typed.ActorSystem[Nothing] = classicSystem.toTyped

  @SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
  private val eventHandlers =
    AppTenant.values
      .flatMap { tenant =>
        val childDesign  = newDesign.bind[AppTenant].toInstance(tenant)
        val childSession = session.newChildSession(childDesign)
        Seq(
          childSession.build[ECPaymentIssuingServiceEventHandler],
        )
      }

  /** ReadModelUpdater(Singleton) を起動する
    * Singleton は Cluster内のどこかに起動し、その中で ReadModelUpdater(Stream)が run する
    * すでに起動している状態で呼び出してもOK
    */
  def startReadModelUpdaters(): Unit = {
    eventHandlers.foreach { eventHandler =>
      start(eventHandler)
    }
  }

  private def start[Event <: SalesDetailDomainEvent](eventHandler: SalesDetailEventHandler[Event]) = {
    import eventHandler.tenant

    val readJournalPluginId = s"jp.co.tis.lerna.payment.application.persistence.cassandra.tenants.${tenant.id}.query"

    def sourceProvider(tag: String) = EventSourcedProvider
      .eventsByTag[Event](
        system = system,
        readJournalPluginId = readJournalPluginId,
        tag = tag,
      )

    def projection(tag: String) = SlickProjection
      // offset の更新と handler の DBIO が同一トランザクションのため遅い。handler側が冪等ならば、 SlickProjection.atLeastOnce を使用すると良い。
      .exactlyOnce(
        projectionId = ProjectionId(eventHandler.domainEventTagPrefix, tag),
        sourceProvider = sourceProvider(tag),
        jdbcService.dbConfig,
        handler = () => eventHandler,
      ).withRecoveryStrategy(HandlerRecoveryStrategy.skip)

    ShardedDaemonProcess(system).init[ProjectionBehavior.Command](
      name = s"${tenant.id}-${eventHandler.domainEventTagPrefix}", // TODO: name重複の可能性に注意
      numberOfInstances = eventHandler.domainEventTags.size,
      behaviorFactory = (i: Int) => ProjectionBehavior(projection(eventHandler.domainEventTags(i))),
      stopMessage = ProjectionBehavior.Stop,
    )
  }
}
