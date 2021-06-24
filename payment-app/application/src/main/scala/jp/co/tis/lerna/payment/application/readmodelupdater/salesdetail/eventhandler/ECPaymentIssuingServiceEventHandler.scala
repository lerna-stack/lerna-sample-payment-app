package jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.eventhandler

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._
import jp.co.tis.lerna.payment.adapter.notification.HouseMoneySettlementNotification
import jp.co.tis.lerna.payment.adapter.notification.util.model.{
  HouseMoneySettlementNotificationRequest,
  NotificationFailure,
  NotificationRequest,
  NotificationResponse,
  NotificationSuccess,
}
import jp.co.tis.lerna.payment.adapter.wallet.{ CustomerId, WalletId }
import jp.co.tis.lerna.payment.application.ecpayment.issuing.actor._
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.{
  EventPersistenceInfo,
  HasSalesDetailDomainEventTag,
  SalesDetailEventHandler,
}
import jp.co.tis.lerna.payment.readmodel.constant._
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.utility.AppRequestContext
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.log.AppLogging
import lerna.util.time.LocalDateTimeFactory
import lerna.util.trace.TraceId
import slick.dbio

import scala.concurrent.{ ExecutionContext, Future }

object ECPaymentIssuingServiceEventHandler extends HasSalesDetailDomainEventTag {
  val categoryName: String = "ECHouseMoney"

  override def numberOfTags: Int = 50
}

class ECPaymentIssuingServiceEventHandler(
    val tables: Tables,
    val dateTimeFactory: LocalDateTimeFactory,
    settlementNotification: HouseMoneySettlementNotification,
)(implicit
    val tenant: AppTenant,
    system: ActorSystem[Nothing],
) extends SalesDetailEventHandler[ECPaymentIssuingServiceSalesDetailDomainEvent]
    with AppLogging {

  override def categoryName: String = ECPaymentIssuingServiceEventHandler.categoryName
  override def numberOfTags: Int    = ECPaymentIssuingServiceEventHandler.numberOfTags

  import tables.profile.api._

  private[this] def notice(
      notificationRequest: Option[NotificationRequest],
  )(implicit executionContext: ExecutionContext, traceId: TraceId): Future[NotificationResponse] = {
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

  override def process(envelope: EventEnvelope[ECPaymentIssuingServiceSalesDetailDomainEvent]): dbio.DBIO[Done] = {
    val event = envelope.event

    import system.executionContext
    implicit val traceId: TraceId = event.traceId
    implicit val eventPersistenceInfo: EventPersistenceInfo =
      EventPersistenceInfo(envelope.persistenceId, envelope.sequenceNr)

    for {
      alreadyUpdated <- checkAlreadyUpdated(envelope)
      _ <- {
        if (alreadyUpdated) {
          DBIO.successful(Done)
        } else {
          handle(event)
            .map { maybeNotificationRequest =>
              // 通知は非同期で良いので結果を待たない
              // FIXME: 同時リクエスト数が増えすぎないようにする
              notice(maybeNotificationRequest)
              Done
            }
            .cleanUp(sendDelayTimeMetricsIfNeeded(_, envelope), keepFailure = true)
            .cleanUp(logEventInfoIfFailed(_, envelope), keepFailure = true)
        }
      }
    } yield {
      Done
    }
  }

  private def sendDelayTimeMetricsIfNeeded(
      maybeThrowable: Option[Throwable],
      envelope: EventEnvelope[_],
  )(implicit
      traceId: TraceId,
  ): DBIO[Done.type] = {
    if (maybeThrowable.isEmpty) {
      sendDelayTimeMetrics(envelope.offset)
    }
    DBIO.successful(Done)
  }

  private def logEventInfoIfFailed(
      maybeThrowable: Option[Throwable],
      envelope: EventEnvelope[_],
  )(implicit
      traceId: TraceId,
  ): DBIO[Done] = {
    maybeThrowable match {
      case None => // do nothing
      case Some(throwable) =>
        val message = s"""ReadModelの更新に失敗しました。
                         |persistenceId: ${envelope.persistenceId},
                         |sequenceNr: ${envelope.sequenceNr},
                         |event: ${envelope.event}
                         |""".stripMargin
        logger.error(throwable, message)
    }
    DBIO.successful(Done)
  }

  def handle(event: ECPaymentIssuingServiceSalesDetailDomainEvent)(implicit
      executionContext: ExecutionContext,
      eventPersistenceInfo: EventPersistenceInfo,
      appRequestContext: AppRequestContext,
  ): DBIO[Option[NotificationRequest]] = {
    event match {
      case event: SettlementSuccessConfirmed =>
        generateNotificationRequestForSettlementSuccess(event)
      case event: SettlementFailureConfirmed =>
        generateNotificationRequestForFailed(Left(event))
      case event: CancelSuccessConfirmed =>
        generateNotificationRequestForCancelSettlementSuccess(event)
      case event: CancelFailureConfirmed =>
        generateNotificationRequestForFailed(Right(event))
    }
  }

  /** 決済成功
    *
    * @param paymentSuccess イベント(決済成功)
    */
  private def generateNotificationRequestForSettlementSuccess(
      paymentSuccess: SettlementSuccessConfirmed,
  )(implicit
      executionContext: ExecutionContext,
      appRequestContext: AppRequestContext,
      eventPersistenceInfo: EventPersistenceInfo,
  ): DBIO[Option[HouseMoneySettlementNotificationRequest]] = {
    val amountTran = paymentSuccess.issuingServiceRequest.amountTran // 取引金額

    val systemDate = paymentSuccess.systemDate // 取引日
    val saleDate   = paymentSuccess.systemDate // 買上日時
    val sendDate   = paymentSuccess.systemDate // 送信日時

    for {
      cashBackAmount <- fetchCashBackTempAmount(
        settlementType = SettlementType.house,
        amountTran.toBigDecimal,
        systemDate = systemDate,
      )
      walletSettlementId <- fetchSalesDetailSeq()
      _ <- ignoreDuplicate(
        insertToSalesDetail(
          walletSettlementId = walletSettlementId,
          walletId = paymentSuccess.payCredential.walletId,
          customerNumber = paymentSuccess.payCredential.customerNumber,
          intranid = Option(paymentSuccess.paymentResponse.intranid),
          originDealId = None, // 元取引特定情報
          contractNumber = paymentSuccess.payCredential.contractNumber,
          pan = paymentSuccess.payCredential.housePan,
          saleDatetime = Timestamp.valueOf(saleDate),
          systemDate = systemDate,
          saleCancelType = SaleCancelType.sale,
          sendDatetime = Option(Timestamp.valueOf(sendDate)),
          authId = Option(paymentSuccess.paymentResponse.authId),
          amountTran = amountTran,
          accptrId = paymentSuccess.issuingServiceRequest.accptrId,
          terminalId = paymentSuccess.payCredential.terminalId,
          accptrName = paymentSuccess.payCredential.memberStoreNameJp,
          errorCode = Option(paymentSuccess.paymentResponse.rErrcode),
          transactionId = paymentSuccess.issuingServiceRequest.transactionId,
          paymentId = paymentSuccess.issuingServiceRequest.paymentId,
          originalPaymentId = None,
          cashBackTempAmount = cashBackAmount,
          isApplicationExtractable = true,
          customerId = paymentSuccess.requestInfo.customerId,
        ),
      )
    } yield Option(HouseMoneySettlementNotificationRequest(walletSettlementId.toString()))
  }

  /** 決済取消成功。
    *
    * @param cancelSucceeded イベント(決済取消成功)
    */
  private def generateNotificationRequestForCancelSettlementSuccess(
      cancelSucceeded: CancelSuccessConfirmed,
  )(implicit
      executionContext: ExecutionContext,
      appRequestContext: AppRequestContext,
      eventPersistenceInfo: EventPersistenceInfo,
  ): DBIO[Option[HouseMoneySettlementNotificationRequest]] = {

    val systemDate = cancelSucceeded.systemDateTime // 取引日
    val saleDate   = cancelSucceeded.saleDateTime   // 買上日時
    val sendDate   = cancelSucceeded.saleDateTime   // 送信日時

    for {
      cashBackAmount     <- getCashBackAmount(eventPersistenceInfo.eventPersistenceId)
      walletSettlementId <- fetchSalesDetailSeq()
      _ <- ignoreDuplicate(
        insertToSalesDetail(
          walletSettlementId = walletSettlementId,
          walletId = cancelSucceeded.payCredential.walletId,
          customerNumber = cancelSucceeded.payCredential.customerNumber,
          intranid = Option(cancelSucceeded.cancelResponse.intranid),
          originDealId = Option(cancelSucceeded.specificDealInfo), // 元取引特定情報
          contractNumber = cancelSucceeded.payCredential.contractNumber,
          pan = cancelSucceeded.payCredential.housePan,
          saleDatetime = Timestamp.valueOf(saleDate),
          systemDate = systemDate,
          saleCancelType = SaleCancelType.cancel,
          sendDatetime = Option(Timestamp.valueOf(sendDate)),
          authId = Option(cancelSucceeded.cancelResponse.authId),
          amountTran = cancelSucceeded.originalRequest.amountTran,
          accptrId = cancelSucceeded.originalRequest.accptrId,
          terminalId = cancelSucceeded.payCredential.terminalId,
          accptrName = cancelSucceeded.payCredential.memberStoreNameJp,
          errorCode = Option(cancelSucceeded.cancelResponse.rErrcode),
          transactionId = cancelSucceeded.acquirerReversalRequestParameter.transactionId,
          paymentId = cancelSucceeded.acquirerReversalRequestParameter.paymentId,
          originalPaymentId = Option(cancelSucceeded.originalRequest.paymentId),
          cashBackTempAmount = cashBackAmount,
          isApplicationExtractable = true,
          customerId = cancelSucceeded.requestInfo.customerId,
        ),
      )
    } yield Option(HouseMoneySettlementNotificationRequest(walletSettlementId.toString()))
  }

  /** 決済失敗,決済取消失敗。
    *
    * @param event イベント(決済失敗,決済取消失敗)
    */
  private def generateNotificationRequestForFailed(event: Either[SettlementFailureConfirmed, CancelFailureConfirmed])(
      implicit
      executionContext: ExecutionContext,
      appRequestContext: AppRequestContext,
      eventPersistenceInfo: EventPersistenceInfo,
  ): DBIO[Option[HouseMoneySettlementNotificationRequest]] = {
    val payResponse = event.fold(_.payResponse, _.cancelResponse)

    val payCredential = event.fold(_.payCredential, _.payCredential)

    val transactionId =
      event.fold(_.issuingServiceRequest.transactionId, _.acquirerReversalRequestParameter.transactionId)
    val paymentId =
      event.fold(_.issuingServiceRequest.paymentId, _.acquirerReversalRequestParameter.paymentId)

    for {
      walletSettlementId <- fetchSalesDetailSeq()
      _ <- ignoreDuplicate(
        insertToSalesDetail(
          walletSettlementId = walletSettlementId,
          walletId = payCredential.walletId,
          customerNumber = payCredential.customerNumber,
          intranid = payResponse.map(_.intranid),
          originDealId = event.right.map(_.paymentResponse.intranid).toOption,
          contractNumber = payCredential.contractNumber,
          pan = payCredential.housePan,
          saleDatetime = Timestamp.valueOf(event.fold(_.systemDate, _.saleDateTime)), // 買上日時
          systemDate = event.fold(_.systemDate, _.systemDateTime),
          saleCancelType = if (event.isLeft) SaleCancelType.sale else SaleCancelType.cancel,
          sendDatetime = Option(Timestamp.valueOf(event.fold(_.systemDate, _.saleDateTime))), // 送信日時
          authId = payResponse.map(_.authId),
          amountTran = event.fold(_.requestInfo.amountTran, _.originalRequest.amountTran),
          accptrId = payCredential.memberStoreId,
          terminalId = payCredential.terminalId,
          accptrName = payCredential.memberStoreNameJp,
          errorCode = getError(payResponse.map(_.rErrcode)),
          transactionId = transactionId,
          paymentId = paymentId,
          originalPaymentId = event.right.map(_.originalRequest.paymentId).toOption,
          cashBackTempAmount = None,
          isApplicationExtractable = false,
          customerId = event.fold(_.requestInfo.customerId, _.requestInfo.customerId),
        ),
      )
    } yield None
  }

  /** @param eventPersistenceId イベント永続化ID
    */
  private def getCashBackAmount(
      eventPersistenceId: String,
  )(implicit executionContext: ExecutionContext, appRequestContext: AppRequestContext): DBIO[Option[BigDecimal]] = {
    import tables._
    SalesDetail
      .filter(_.eventPersistenceId === eventPersistenceId)         // イベント永続化ID
      .filter(_.saleCancelType === SaleCancelType.sale)            // 区分(売・取)
      .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
      .map(_.cashBackTempAmount)
      .result
      .map {
        case Nil =>
          // 元取引のキャシュバック金額が何かしらの理由でとれない場合、NULLを設定
          // バグの可能性が高いので、エラーログを出力
          logger.error("マスタデータの取得に失敗しました。Walletシステム取引ID：不明, 取得対象テーブル：SalesDetail, 取得対象項目：cashBackTempAmount")
          None
        case Seq(Some(cashBackTempAmount)) =>
          Option(cashBackTempAmount)
        case Seq(None) =>
          // 元取引のキャシュバック金額が何かしらの理由でとれない場合、NULLを設定
          // バグの可能性が高いので、エラーログを出力
          logger.error("マスタデータの取得に失敗しました。Walletシステム取引ID：不明, 取得対象テーブル：SalesDetail, 取得対象項目：cashBackTempAmount.")
          None
        case results =>
          // 2件以上（履歴不備）
          // ※ 必ず1件になるように運用する
          logger.error(
            s"SalesDetail.eventPersistenceId === $eventPersistenceId & saleCancelType === 売上(01) の取得結果が ${results.length} 件です。",
          )
          None
      }
  }

  private def getError(maybeErrors: Option[String]): Option[String] = {
    maybeErrors match {
      case Some(error) if !error.isEmpty => Option(error)
      case _                             => Option("unexpected_error")
    }
  }

  private def insertToSalesDetail(
      walletSettlementId: BigDecimal,
      walletId: Option[WalletId],
      customerNumber: Option[String],
      intranid: Option[IntranId],
      originDealId: Option[IntranId],
      contractNumber: String,
      pan: HousePan,
      saleDatetime: java.sql.Timestamp,
      systemDate: LocalDateTime,
      saleCancelType: String,
      sendDatetime: Option[java.sql.Timestamp],
      authId: Option[String],
      amountTran: AmountTran,
      accptrId: String,
      terminalId: TerminalId,
      accptrName: Option[String],
      errorCode: Option[String],
      transactionId: TransactionId,
      paymentId: PaymentId,
      originalPaymentId: Option[PaymentId],
      cashBackTempAmount: Option[scala.math.BigDecimal],
      isApplicationExtractable: Boolean,
      customerId: CustomerId,
  )(implicit
      eventPersistenceInfo: EventPersistenceInfo,
  ): DBIO[Int] = {
    val sysDate = Timestamp.valueOf(dateTimeFactory.now()) // システム時間

    val applicationExtractFlag = if (isApplicationExtractable) {
      ApplicationExtractFlag.extractable
    } else {
      ApplicationExtractFlag.nonExtractable
    }

    tables.SalesDetail += tables.SalesDetailRow(
      walletSettlementId = walletSettlementId,
      settlementType = Option(SettlementType.house),
      walletId = walletId.map(_.value),
      customerNumber = customerNumber,
      dealStatus = Option(DealStatus.completed),
      specificDealInfo = intranid.map(_.value),
      originDealId = originDealId.map(_.value),
      contractNumber = Option(contractNumber),
      maskingInfo = Option(pan.value),
      saleDatetime = Option(saleDatetime),                                           // yyyyMMddHHmmss
      dealDate = Option(Timestamp.valueOf(systemDate.truncatedTo(ChronoUnit.DAYS))), // yyyyMMdd
      saleCancelType = Option(saleCancelType),
      sendDatetime = sendDatetime,
      authoriNumber = authId.filter(_.nonEmpty).map(BigDecimal.apply),
      amount = Option(amountTran.toBigDecimal),
      memberStoreId = Option(accptrId),
      memberStorePosId = Option(terminalId.value),
      memberStoreName = accptrName,
      failureCancelFlag = Option(BigDecimal(0)), // 障害取消フラグ
      errorCode = errorCode,
      dealSerialNumber = Option(transactionId.value),
      paymentId = Option(paymentId.value),
      originalPaymentId = originalPaymentId.map(_.value),
      cashBackTempAmount = cashBackTempAmount,
      cashBackFixedAmount = None,
      applicationExtractFlag = Option(applicationExtractFlag),
      customerId = Option(customerId.value),
      saleExtractedFlag = Option(BigDecimal(0)), // 売上連携済フラグ
      eventPersistenceId = Option(eventPersistenceInfo.eventPersistenceId),
      eventSequenceNumber = Option(BigDecimal(eventPersistenceInfo.eventSequenceNumber)),
      insertDate = sysDate,                           // 登録日時
      insertUserId = SystemIdentify.name,             // 登録ユーザーID
      updateDate = Option(sysDate),                   // 更新日時
      updateUserId = Option(SystemIdentify.name),     // 更新ユーザーID
      versionNo = BigDecimal(0),                      // バージョン番号
      logicalDeleteFlag = LogicalDeleteFlag.unDeleted,// 論理削除フラグ
    )
  }
}
