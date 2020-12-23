package jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.detail

import akka.persistence.query.EventEnvelope
import jp.co.tis.lerna.payment.adapter.notification.HouseMoneySettlementNotification
import jp.co.tis.lerna.payment.adapter.notification.util.model._
import jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.ECPaymentIssuingServiceSalesDetailDomainEvent
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.eventhandler.ECPaymentIssuingServiceEventHandler
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.{
  EventPersistenceInfo,
  HasSalesDetailDomainEventTag,
  SalesDetailReadModelUpdater,
}
import jp.co.tis.lerna.payment.readmodel.JDBCService
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.utility.AppRequestContext
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.util.time.LocalDateTimeFactory
import lerna.util.trace.TraceId
import slick.dbio.DBIO

import scala.concurrent.{ ExecutionContext, Future }

object ECHouseMoneySalesDetailReadModelUpdater extends HasSalesDetailDomainEventTag {
  val categoryName: String = "ECHouseMoney"
}

class ECHouseMoneySalesDetailReadModelUpdater(
    val jdbcService: JDBCService,
    val tables: Tables,
    val dateTimeFactory: LocalDateTimeFactory,
    settlementNotification: HouseMoneySettlementNotification,
    eventHandler: ECPaymentIssuingServiceEventHandler,
)(implicit val tenant: AppTenant)
    extends SalesDetailReadModelUpdater {

  override def categoryName: String = ECHouseMoneySalesDetailReadModelUpdater.categoryName

  override protected[this] def updateReadModel(
      eventEnvelope: EventEnvelope,
  )(implicit traceId: TraceId, executionContext: ExecutionContext): DBIO[Option[NotificationRequest]] = {
    implicit val eventPersistenceInfo: EventPersistenceInfo =
      EventPersistenceInfo(eventEnvelope.persistenceId, eventEnvelope.sequenceNr)
    eventEnvelope.event match {
      case event: ECPaymentIssuingServiceSalesDetailDomainEvent =>
        eventHandler.handle(event)
      case _ =>
        logger.warn("ECPaymentIssuingServiceSalesDetailDomainEventではないイベントが永続化されています: {}", eventEnvelope)
        DBIO.successful(None)
    }
  }

  override protected[this] def notice(
      notificationRequest: Option[NotificationRequest],
  )(implicit executionContext: ExecutionContext, traceId: TraceId): Future[NotificationResponse] = {
    implicit val appRequestContext: AppRequestContext = AppRequestContext(traceId, tenant)
    val res = notificationRequest match {
      case Some(HouseMoneySettlementNotificationRequest(walletSettlementId)) =>
        settlementNotification.notice(walletSettlementId)
      case None => Future.successful(NotificationSuccess())
      case _    => Future.successful(NotificationFailure())
    }

    res.recover {
      case _ => NotificationFailure()
    }
  }
}
