package jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.eventhandler

import java.sql.Timestamp
import java.time.temporal.ChronoUnit
import java.time.{ LocalDateTime, Month }
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.adapter.ecpayment.model.{ OrderId, WalletShopId }
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._
import jp.co.tis.lerna.payment.adapter.notification.util.model.{
  HouseMoneySettlementNotificationRequest,
  NotificationResponse,
  NotificationSuccess,
}
import jp.co.tis.lerna.payment.adapter.issuing.model.{
  AcquirerReversalRequestParameter,
  AuthorizationRequestParameter,
  IssuingServiceResponse,
}
import jp.co.tis.lerna.payment.adapter.notification.HouseMoneySettlementNotification
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.adapter.util.{
  IssuingServiceBadRequestError,
  IssuingServiceServerError,
  UnpredictableError,
}
import jp.co.tis.lerna.payment.adapter.wallet.{ ClientId, CustomerId, WalletId }
import jp.co.tis.lerna.payment.application.ApplicationDIDesign
import jp.co.tis.lerna.payment.application.ecpayment.issuing.IssuingServicePayCredential
import jp.co.tis.lerna.payment.application.ecpayment.issuing.actor._
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.EventPersistenceInfo
import jp.co.tis.lerna.payment.readmodel.constant.{ LogicalDeleteFlag, SaleCancelType }
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.readmodel.{ JDBCSupport, ReadModelDIDesign }
import jp.co.tis.lerna.payment.utility.{ AppRequestContext, UtilityDIDesign }
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.{ AppTenant, Example }
import lerna.testkit.airframe.DISessionSupport
import lerna.util.time.{ FixedLocalDateTimeFactory, LocalDateTimeFactory }
import lerna.util.trace.TraceId
import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import wvlet.airframe.Design

import scala.concurrent.Future

// Lint回避のため
@SuppressWarnings(
  Array(
    "lerna.warts.Awaits",
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
class ECPaymentIssuingServiceEventHandlerSpec
    extends TestKit(ActorSystem("ECPaymentIssuingServiceEventHandlerSpec"))
    with StandardSpec
    with DISessionSupport
    with JDBCSupport
    with ScalaFutures
    with Inside {

  import system.dispatcher
  override def afterDatabasePrepared(): Unit = {
    super.afterDatabasePrepared()
    createWalletSettlementIdSeq()
  }

  override protected val diDesign: Design = UtilityDIDesign.utilityDesign
    .add(ReadModelDIDesign.readModelDesign)
    .add(ApplicationDIDesign.applicationDesign)
    .bind[ActorSystem].toInstance(system)
    .bind[Config].toInstance(ConfigFactory.load())
    .bind[LocalDateTimeFactory].toInstance(FixedLocalDateTimeFactory("2019-05-01T11:22:33Z"))
    .bind[AppTenant].toInstance(tenant)
    .bind[HouseMoneySettlementNotification].toInstance(new HouseMoneySettlementNotification {
      override def notice(walletSettlementId: String)(implicit
          appRequestContext: AppRequestContext,
      ): Future[NotificationResponse] = {
        // TODO: mockito を使用して呼び出しチェック
        // do nothing
        Future.successful(NotificationSuccess())
      }
    })

  private val generateUniqueNumber: () => Int = {
    val counter = new AtomicInteger(100)
    () => counter.getAndIncrement()
  }

  private val incentiveRate = BigDecimal.double2bigDecimal(0.01)

  val issuingServicePaymentRequest1 = AuthorizationRequestParameter(
    pan = HousePan("4001123456789013"),
    amountTran = AmountTran(generateUniqueNumber()),
    tranDateTime = LocalDateTime.of(2019, 12, 31, 12, 34, 56),
    transactionId = TransactionId(generateUniqueNumber()),
    accptrId = s"${generateUniqueNumber()}",
    paymentId = PaymentId(generateUniqueNumber()),
    terminalId = TerminalId(s"${generateUniqueNumber()}"),
  )

  val acquirerReversalRequestParameter = AcquirerReversalRequestParameter(
    transactionId = TransactionId(generateUniqueNumber()),
    paymentId = PaymentId(generateUniqueNumber()),
    terminalId = TerminalId(s"${generateUniqueNumber()}"),
  )

  val eventPersistenceId: String = "dummy-persistence-id"
  // 正の整数ならいくつでも良いが、 0 は初期値でデフォルト値と区別がつかないと思われる可能性があるのでその他の適当な値を使用
  val eventSequenceNumber: Long = 42

  implicit val eventPersistenceInfo: EventPersistenceInfo =
    EventPersistenceInfo(eventPersistenceId, eventSequenceNumber)

  private implicit def appRequestContext(implicit traceId: TraceId): AppRequestContext =
    AppRequestContext(traceId, tenant = Example)

  "決済" when {

    val date         = diSession.build[LocalDateTimeFactory]
    val eventHandler = diSession.build[ECPaymentIssuingServiceEventHandler]
    import tableSeeds._
    import tables._
    import tables.profile.api._

    "決済成功" in withJDBC { db =>
      implicit val traceId: TraceId = TraceId("traceId0001")
      val customerId                = CustomerId("customerId0001")
      val walletId: String          = "walletId0001"
      val customerNumber            = "number0001"
      db.prepare(
        IncentiveMaster += IncentiveMasterRowSeed.copy(
          incentiveMasterId = 1,
          settlementType = "01",
          incentiveType = "01",
          incentiveRate = Option(incentiveRate),
          incentiveDateFrom = Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)),
          incentiveDateTo = Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)),
        ),
      )

      val event =
        SettlementSuccessConfirmed(
          IssuingServiceResponse(
            intranid = IntranId("888"),
            authId = "999",
            rErrcode = "00000",
          ),
          IssuingServicePayCredential(
            walletId = Option(WalletId("walletId0001")),
            customerNumber = Option("number0001"),
            memberStoreId = "369",
            memberStoreNameEn = Option("1"),
            memberStoreNameJp = Option("加盟店名"),
            contractNumber = "333",
            housePan = HousePan("666"),
            terminalId = TerminalId("12345678"),
          ),
          Settle(
            clientId = ClientId(11),
            customerId = CustomerId("customerId0001"),
            walletShopId = WalletShopId(""),
            orderId = OrderId("33"),
            amountTran = AmountTran(44),
          ),
          SettlementSuccessResponse(),
          issuingServicePaymentRequest1,
          date.now(),
        )

      whenReady(jdbcService.db.run(eventHandler.handle(event).transactionally)) { res =>
        expect { res === Option(HouseMoneySettlementNotificationRequest("1")) }
      }

      val action = SalesDetail
        .filter(_.customerId === customerId.value)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
        .result.headOption
      whenReady(jdbcService.db.run(action)) {
        inside(_) {
          case Some(result) =>
            expect {
              result.walletSettlementId === BigDecimal(1)
              result.walletId === Option(walletId.value)
              result.customerNumber === Option(customerNumber)
              result.dealStatus === Option("00")
              result.specificDealInfo === Option("888")
              result.originDealId === None
              result.maskingInfo === Option(event.payCredential.housePan.value)
              result.saleCancelType === Option("01")
              result.amount === Option(issuingServicePaymentRequest1.amountTran.toBigDecimal)
              result.memberStoreId === Option(issuingServicePaymentRequest1.accptrId)
              result.memberStoreName === Option("加盟店名")
              result.failureCancelFlag === Option(BigDecimal(0))
              result.originalPaymentId === None
              result.cashBackTempAmount === Option(
                issuingServicePaymentRequest1.amountTran.toBigDecimal * incentiveRate,
              )
              result.cashBackFixedAmount === None
              result.applicationExtractFlag === Option(BigDecimal(1))
              result.customerId === Option(customerId.value)
              result.saleExtractedFlag === Option(BigDecimal(0))
              result.settlementType === Option("01")
              result.contractNumber === Option("333")
              result.saleDatetime === Option(Timestamp.valueOf(date.now()))
              result.dealDate === Option(Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)))
              result.sendDatetime === Option(Timestamp.valueOf(date.now()))
              result.authoriNumber === Option(BigDecimal(999))
              result.memberStorePosId === Option("12345678")
              result.errorCode === Option("00000")
              result.dealSerialNumber === Option(issuingServicePaymentRequest1.transactionId.value)
              result.paymentId === Option(issuingServicePaymentRequest1.paymentId.value)
              result.eventPersistenceId === Option(eventPersistenceId)
              result.eventSequenceNumber === Option(BigDecimal(eventSequenceNumber))
              result.versionNo === BigDecimal(0)
              result.insertDate === Timestamp.valueOf(date.now())
              result.insertUserId === "payment-app"
              result.updateDate === Option(Timestamp.valueOf(date.now()))
              result.updateUserId === Option("payment-app")
              result.logicalDeleteFlag === BigDecimal(0)
            }
        }
      }
    }

    "決済失敗 レスポンス情報なし causeにjson情報があり" in withJDBC { db =>
      implicit val traceId: TraceId = TraceId("traceId0001")
      val customerId                = CustomerId("customerId0001")
      db.prepare(
        IncentiveMaster += IncentiveMasterRowSeed.copy(
          incentiveMasterId = 1,
          settlementType = "01",
          incentiveType = "01",
          incentiveRate = Option(incentiveRate),
          incentiveDateFrom = Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)),
          incentiveDateTo = Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)),
        ),
      )

      val event =
        SettlementFailureConfirmed(
          None,
          IssuingServicePayCredential(
            walletId = Option(WalletId("walletId0002")),
            customerNumber = Option("number0002"),
            memberStoreId = "369",
            memberStoreNameEn = Option("1"),
            memberStoreNameJp = Option("加盟店名"),
            contractNumber = "333",
            housePan = HousePan("666"),
            terminalId = TerminalId("555"),
          ),
          Settle(
            clientId = ClientId(11),
            customerId = CustomerId("customerId0001"),
            walletShopId = WalletShopId(""),
            orderId = OrderId("33"),
            amountTran = AmountTran(44),
          ),
          issuingServicePaymentRequest1,
          cause = new BusinessException(message = IssuingServiceBadRequestError("")),
          date.now(),
        )

      whenReady(jdbcService.db.run(eventHandler.handle(event).transactionally)) { res =>
        res mustBe None
      }

      val action = SalesDetail
        .filter(_.customerId === customerId.value)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
        .result.headOption
      whenReady(jdbcService.db.run(action)) {
        inside(_) {
          case Some(result) =>
            expect {
              result.walletSettlementId === BigDecimal(2)
              result.walletId === Option("walletId0002")
              result.customerNumber === Option("number0002")
              result.dealStatus === Option("00")
              result.maskingInfo === Option(event.payCredential.housePan.value)
              result.specificDealInfo === None
              result.saleCancelType === Option("01")
              result.errorCode === Option("unexpected_error")
              result.amount === Option(BigDecimal(44))
              result.memberStoreId === Option("369")
              result.memberStoreName === Option("加盟店名")
              result.cashBackTempAmount === None
              result.applicationExtractFlag === Option(BigDecimal(0))
              result.customerId === Option(customerId.value)
              result.dealSerialNumber === Option(issuingServicePaymentRequest1.transactionId.value) // 取引ID
              result.paymentId === Option(issuingServicePaymentRequest1.paymentId.value)            // (会員ごと)決済番号
              result.eventPersistenceId === Option(eventPersistenceId)
              result.eventSequenceNumber === Option(BigDecimal(eventSequenceNumber))
            }
        }
      }
    }

    "決済失敗 レスポンス情報があり" in withJDBC { db =>
      implicit val traceId: TraceId = TraceId("traceId0001")
      val customerId                = CustomerId("customerId0001")
      val walletId                  = WalletId("walletId0001")
      val customerNumber            = "number0001"
      db.prepare(
        IncentiveMaster += IncentiveMasterRowSeed.copy(
          incentiveMasterId = 1,
          settlementType = "01",
          incentiveType = "01",
          incentiveRate = Option(incentiveRate),
          incentiveDateFrom = Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)),
          incentiveDateTo = Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)),
        ),
      )

      val event =
        SettlementFailureConfirmed(
          Option(
            IssuingServiceResponse(
              intranid = IntranId("888"),
              authId = "",
              rErrcode = "",
            ),
          ),
          IssuingServicePayCredential(
            walletId = Option(walletId),
            customerNumber = Option("number0001"),
            memberStoreId = "369",
            memberStoreNameEn = Option("1"),
            memberStoreNameJp = Option("加盟店名"),
            contractNumber = "333",
            housePan = HousePan("666"),
            terminalId = TerminalId("555"),
          ),
          Settle(
            clientId = ClientId(11),
            customerId = CustomerId("customerId0001"),
            walletShopId = WalletShopId(""),
            orderId = OrderId("33"),
            amountTran = AmountTran(44),
          ),
          issuingServicePaymentRequest1,
          cause = new BusinessException(UnpredictableError()),
          date.now(),
        )

      whenReady(jdbcService.db.run(eventHandler.handle(event).transactionally)) { res =>
        res mustBe None
      }

      val action = SalesDetail
        .filter(_.customerId === customerId.value)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
        .result.headOption
      whenReady(jdbcService.db.run(action)) {
        inside(_) {
          case Some(result) =>
            expect {
              result.walletSettlementId === BigDecimal(3)
              result.walletId === Option(walletId.value)
              result.customerNumber === Option(customerNumber)
              result.dealStatus === Option("00")
              result.specificDealInfo === Option("888")
              result.maskingInfo === Option(event.payCredential.housePan.value)
              result.saleCancelType === Option("01")
              result.errorCode === Option("unexpected_error")
              result.amount === Option(BigDecimal(44))
              result.memberStoreId === Option("369")
              result.memberStoreName === Option("加盟店名")
              result.cashBackTempAmount === None
              result.applicationExtractFlag === Option(BigDecimal(0))
              result.customerId === Option(customerId.value)
              result.authoriNumber === None
              result.dealSerialNumber === Option(issuingServicePaymentRequest1.transactionId.value) // 取引ID
              result.paymentId === Option(issuingServicePaymentRequest1.paymentId.value)            // (会員ごと)決済番号
              result.eventPersistenceId === Option(eventPersistenceId)
              result.eventSequenceNumber === Option(BigDecimal(eventSequenceNumber))
            }
        }
      }
    }
  }

  "取消" when {
    val date         = diSession.build[LocalDateTimeFactory]
    val eventHandler = diSession.build[ECPaymentIssuingServiceEventHandler]
    import tableSeeds._
    import tables._
    import tables.profile.api._

    "取消成功" in withJDBC { db =>
      db.prepare(
        SalesDetail += SalesDetailRowSeed.copy(
          walletSettlementId = 1,
          settlementType = Option("01"),
          walletId = Option("12"),
          customerNumber = Option("45"),
          dealStatus = Option("78"),
          specificDealInfo = Option("11"),
          originDealId = Option("11"),
          contractNumber = Option("12"),
          maskingInfo = Option("12"),
          saleDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          dealDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          saleCancelType = Option(SaleCancelType.sale),
          sendDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          authoriNumber = Option(BigDecimal(2)),
          amount = Option(BigDecimal(2)),
          memberStoreId = Option("12"),
          memberStorePosId = Option("12"),
          memberStoreName = Option("加盟店名"),
          failureCancelFlag = Option(BigDecimal(2)),
          errorCode = Option("12"),
          dealSerialNumber = Option("12"),
          paymentId = Option("567"),
          originalPaymentId = Option("777"),
          cashBackTempAmount = Option(BigDecimal(100)),
          cashBackFixedAmount = Option(BigDecimal(2)),
          applicationExtractFlag = Option(BigDecimal(2)),
          customerId = Option("789"),
          saleExtractedFlag = Option(BigDecimal(2)),
          eventPersistenceId = Option(eventPersistenceId),
          insertDate = Timestamp.valueOf(LocalDateTime.now()),
          insertUserId = "12",
          updateDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          updateUserId = Option("12"),
          versionNo = BigDecimal(0),
          logicalDeleteFlag = BigDecimal(0),
        ),
      )
      implicit val traceId: TraceId = TraceId("traceId0001")
      val customerId                = "789"
      val walletId                  = WalletId("123")
      val customerNumber            = "333"
      val memberStoreName           = "加盟店名"
      val issuingServiceResponse = IssuingServiceResponse(
        IntranId(""),
        "123",
        "00000",
      )
      val issuingServicePayCredential =
        IssuingServicePayCredential(
          Option(walletId),
          Option(customerNumber),
          "",
          None,
          Option("加盟店名"),
          "369",
          HousePan(""),
          TerminalId("12345678"),
        )
      val issuingServiceCancel      = Cancel(ClientId(333), CustomerId(customerId), WalletShopId(""), OrderId(""))
      val settlementSuccessResponse = SettlementSuccessResponse()
      val systemDate                = date.now()
      val saleDateTime              = LocalDateTime.of(2019, Month.JANUARY, 1, 11, 11, 11)

      val event =
        CancelSuccessConfirmed(
          issuingServiceResponse,
          issuingServicePayCredential,
          issuingServiceCancel,
          settlementSuccessResponse,
          IntranId("originIntranId"),
          saleDateTime,
          acquirerReversalRequestParameter,
          issuingServicePaymentRequest1,
          systemDate,
        )

      whenReady(jdbcService.db.run(eventHandler.handle(event).transactionally)) { _ => }

      val action = SalesDetail
        .filter(_.customerId === customerId.value)
        .filter(_.saleCancelType === "02")
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
        .result.headOption
      whenReady(jdbcService.db.run(action)) {
        inside(_) {
          case Some(result) =>
            expect {
              result.walletSettlementId === BigDecimal(4)
              result.walletId === Option(walletId.value)
              result.customerNumber === Option(customerNumber)
              result.dealStatus === Option("00")
              result.specificDealInfo === Option("")
              result.originDealId === Option("originIntranId")
              result.maskingInfo === Option(issuingServicePayCredential.housePan.value)
              result.saleDatetime === Option(Timestamp.valueOf(LocalDateTime.of(2019, Month.JANUARY, 1, 11, 11, 11)))
              result.dealDate === Option(Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)))
              result.saleCancelType === Option("02")
              result.amount === Option(issuingServicePaymentRequest1.amountTran.toBigDecimal)
              result.memberStoreId === Option(event.originalRequest.accptrId)
              result.memberStoreName === Option(memberStoreName)
              result.failureCancelFlag === Option(BigDecimal(0))
              result.cashBackTempAmount === Option(BigDecimal(100))
              result.applicationExtractFlag === Option(BigDecimal(1))
              result.customerId === Option(customerId.value)
              result.saleExtractedFlag === Option(BigDecimal(0))
              result.settlementType === Option("01")
              result.specificDealInfo === Option("")
              result.contractNumber === Option("369")
              result.saleDatetime === Option(Timestamp.valueOf(LocalDateTime.of(2019, Month.JANUARY, 1, 11, 11, 11)))
              result.dealDate === Option(Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)))
              result.sendDatetime === Option(Timestamp.valueOf(LocalDateTime.of(2019, Month.JANUARY, 1, 11, 11, 11)))
              result.authoriNumber === Option(BigDecimal("123"))
              result.memberStorePosId === Option("12345678")
              result.errorCode === Option("00000")
              result.dealSerialNumber === Option(acquirerReversalRequestParameter.transactionId.value)
              result.paymentId === Option(acquirerReversalRequestParameter.paymentId.value)
              result.versionNo === BigDecimal(0)
              result.eventPersistenceId === Option(eventPersistenceId)
              result.eventSequenceNumber === Option(BigDecimal(eventSequenceNumber))
            }
        }
      }
    }

    "取消失敗 エラー情報があり " in withJDBC { db =>
      db.prepare(
        SalesDetail += SalesDetailRowSeed.copy(
          walletSettlementId = 1,
          settlementType = Option("01"),
          walletId = Option("12"),
          customerNumber = Option("45"),
          dealStatus = Option("78"),
          specificDealInfo = Option("11"),
          originDealId = Option("11"),
          contractNumber = Option("12"),
          maskingInfo = Option("12"),
          saleDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          dealDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          saleCancelType = Option(SaleCancelType.sale),
          sendDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          authoriNumber = Option(BigDecimal(2)),
          amount = Option(BigDecimal(2)),
          memberStoreId = Option("12"),
          memberStorePosId = Option("12"),
          memberStoreName = Option("12"),
          failureCancelFlag = Option(BigDecimal(2)),
          errorCode = Option("12"),
          dealSerialNumber = Option("12"),
          paymentId = Option("567"),
          originalPaymentId = Option("777"),
          cashBackTempAmount = Option(BigDecimal(200)),
          cashBackFixedAmount = Option(BigDecimal(2)),
          applicationExtractFlag = Option(BigDecimal(2)),
          customerId = Option("12"),
          saleExtractedFlag = Option(BigDecimal(2)),
          eventPersistenceId = Option(eventPersistenceId),
          insertDate = Timestamp.valueOf(LocalDateTime.now()),
          insertUserId = "12",
          updateDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          updateUserId = Option("12"),
          versionNo = BigDecimal(0),
          logicalDeleteFlag = BigDecimal(0),
        ),
      )
      implicit val traceId: TraceId = TraceId("traceId0001")
      val customerId                = "789"
      val walletId                  = WalletId("123")
      val customerNumber            = "333"
      val issuingServiceResponse = IssuingServiceResponse(
        IntranId("999"),
        authId = "",
        "errorCode001",
      )
      val issuingServiceResponseErr = IssuingServiceResponse(
        IntranId(""),
        "123",
        "",
      )
      val issuingServicePayCredential =
        IssuingServicePayCredential(
          Option(walletId),
          Option(customerNumber),
          "memberStoreId",
          Option("memberStoreName"),
          Option("加盟店名"),
          "369",
          HousePan("159"),
          TerminalId(""),
        )
      val issuingServiceCancel = Cancel(ClientId(333), CustomerId(customerId), WalletShopId(""), OrderId(""))
      val systemDate           = date.now()
      val saleDateTime         = LocalDateTime.of(2019, Month.JANUARY, 1, 11, 11, 11)

      val event =
        CancelFailureConfirmed(
          issuingServiceResponse,
          issuingServiceCancel,
          issuingServicePayCredential,
          Option(issuingServiceResponseErr),
          new BusinessException(IssuingServiceServerError("承認取消送信")),
          issuingServicePaymentRequest1,
          acquirerReversalRequestParameter,
          saleDateTime,
          systemDate,
        )

      whenReady(jdbcService.db.run(eventHandler.handle(event).transactionally)) { res =>
        res mustBe None
      }

      val action = SalesDetail
        .filter(_.customerId === customerId.value)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
        .result.headOption
      whenReady(jdbcService.db.run(action)) {
        inside(_) {
          case Some(result) =>
            expect {
              result.walletSettlementId === BigDecimal(5)
              result.walletId === Option(walletId.value)
              result.customerNumber === Option(customerNumber)
              result.dealStatus === Option("00")
              result.maskingInfo === Option(issuingServicePayCredential.housePan.value)
              result.specificDealInfo === Option("999")
              result.originDealId === Option("")
              result.saleDatetime === Option(Timestamp.valueOf(LocalDateTime.of(2019, Month.JANUARY, 1, 11, 11, 11)))
              result.dealDate === Option(Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)))
              result.sendDatetime === Option(Timestamp.valueOf(LocalDateTime.of(2019, Month.JANUARY, 1, 11, 11, 11)))
              result.saleCancelType === Option("02")
              result.errorCode === Option("errorCode001")
              result.amount === Option(issuingServicePaymentRequest1.amountTran.toBigDecimal)
              result.memberStoreId === Option("memberStoreId")
              result.memberStoreName === Option("加盟店名")
              result.cashBackTempAmount === None
              result.applicationExtractFlag === Option(BigDecimal(0))
              result.customerId === Option(customerId.value)
              result.settlementType === Option("01")
              result.specificDealInfo === Option("999")
              result.contractNumber === Option("369")
              result.authoriNumber === None
              result.memberStorePosId === Option("")
              result.errorCode === Option("errorCode001")
              result.dealSerialNumber === Option(acquirerReversalRequestParameter.transactionId.value) // 取引ID
              result.paymentId === Option(acquirerReversalRequestParameter.paymentId.value)
              result.originalPaymentId === Option(issuingServicePaymentRequest1.paymentId.value)
              result.eventPersistenceId === Option(eventPersistenceId)
              result.eventSequenceNumber === Option(BigDecimal(eventSequenceNumber))
            }
        }
      }
    }

    "取消失敗  payCredentialとresponseがNoneの場合 " in withJDBC { db =>
      db.prepare(
        IncentiveMaster += IncentiveMasterRowSeed.copy(
          incentiveMasterId = 1,
          settlementType = "01",
          incentiveType = "01",
          incentiveRate = Option(incentiveRate),
          incentiveDateFrom = Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)),
          incentiveDateTo = Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)),
        ),
      )
      implicit val traceId: TraceId = TraceId("traceId0001")
      val customerId                = "789"

      val issuingServiceResponse = IssuingServiceResponse(
        IntranId(""),
        "123",
        "errorCode001",
      )
      val issuingServiceCancel = Cancel(ClientId(333), CustomerId(customerId), WalletShopId(""), OrderId(""))
      val systemDate           = date.now()
      val issuingServicePayCredential =
        IssuingServicePayCredential(None, None, "", None, None, "0", HousePan(""), TerminalId(""))
      val saleDateTime = LocalDateTime.of(2019, Month.JANUARY, 1, 11, 11, 11)

      val event =
        CancelFailureConfirmed(
          issuingServiceResponse,
          issuingServiceCancel,
          issuingServicePayCredential,
          None,
          new BusinessException(IssuingServiceServerError("承認取消送信")),
          issuingServicePaymentRequest1,
          acquirerReversalRequestParameter,
          saleDateTime,
          systemDate,
        )

      whenReady(jdbcService.db.run(eventHandler.handle(event).transactionally)) { res =>
        res mustBe None
      }

      val action = SalesDetail
        .filter(_.customerId === customerId.value)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
        .result.headOption
      whenReady(jdbcService.db.run(action)) {
        inside(_) {
          case Some(result) =>
            expect {
              result.walletSettlementId === BigDecimal(6)
              result.walletId === None
              result.customerNumber === None
              result.dealStatus === Option("00")
              result.specificDealInfo === None
              result.originDealId === Option("")
              result.maskingInfo === Option(issuingServicePayCredential.housePan.value)
              result.saleDatetime === Option(Timestamp.valueOf(LocalDateTime.of(2019, Month.JANUARY, 1, 11, 11, 11)))
              result.dealDate === Option(Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)))
              result.saleCancelType === Option("02")
              result.amount === Option(issuingServicePaymentRequest1.amountTran.toBigDecimal)
              result.memberStoreId === Option("")
              result.memberStoreName === None
              result.cashBackTempAmount === None
              result.applicationExtractFlag === Option(BigDecimal(0))
              result.customerId === Option(customerId.value)
              result.eventPersistenceId === Option(eventPersistenceId)
              result.eventSequenceNumber === Option(BigDecimal(eventSequenceNumber))
            }
        }
      }
    }

    "SalesDetailテーブルに合うデータがない" in withJDBC { db =>
      db.prepare(
        SalesDetail += SalesDetailRowSeed.copy(
          walletSettlementId = 1,
          settlementType = Option("01"),
          walletId = Option("12"),
          customerNumber = Option("45"),
          dealStatus = Option("78"),
          specificDealInfo = Option("11"),
          originDealId = Option("11"),
          contractNumber = Option("12"),
          maskingInfo = Option("12"),
          saleDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          dealDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          saleCancelType = Option(SaleCancelType.sale),
          sendDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          authoriNumber = Option(BigDecimal(2)),
          amount = Option(BigDecimal(2)),
          memberStoreId = Option("12"),
          memberStorePosId = Option("12"),
          memberStoreName = Option("12"),
          failureCancelFlag = Option(BigDecimal(2)),
          errorCode = Option("12"),
          dealSerialNumber = Option("12"),
          paymentId = Option("567"),
          originalPaymentId = Option("777"),
          cashBackTempAmount = Option(BigDecimal(200)),
          cashBackFixedAmount = Option(BigDecimal(2)),
          applicationExtractFlag = Option(BigDecimal(2)),
          customerId = Option("12"),
          saleExtractedFlag = Option(BigDecimal(2)),
          eventPersistenceId = Option(s"other-$eventPersistenceId"),
          insertDate = Timestamp.valueOf(LocalDateTime.now()),
          insertUserId = "12",
          updateDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          updateUserId = Option("12"),
          versionNo = BigDecimal(0),
          logicalDeleteFlag = BigDecimal(0),
        ),
      )
      implicit val traceId: TraceId = TraceId("traceId0001")
      val customerId                = "789"
      val walletId                  = WalletId("123")
      val customerNumber            = "333"
      val issuingServiceResponse = IssuingServiceResponse(
        IntranId(""),
        "123",
        "",
      )
      val issuingServicePayCredential =
        IssuingServicePayCredential(
          Option(walletId),
          Option(customerNumber),
          "",
          None,
          Option("加盟店名"),
          "369",
          HousePan(""),
          TerminalId(""),
        )
      val issuingServiceCancel      = Cancel(ClientId(333), CustomerId(customerId), WalletShopId(""), OrderId(""))
      val settlementSuccessResponse = SettlementSuccessResponse()
      val systemDate                = date.now()
      val saleDateTime              = LocalDateTime.of(2019, Month.JANUARY, 1, 11, 11, 11)

      val event =
        CancelSuccessConfirmed(
          issuingServiceResponse,
          issuingServicePayCredential,
          issuingServiceCancel,
          settlementSuccessResponse,
          IntranId("originIntranId"),
          saleDateTime,
          acquirerReversalRequestParameter,
          issuingServicePaymentRequest1,
          systemDate,
        )

      whenReady(jdbcService.db.run(eventHandler.handle(event).transactionally)) { _ => }

      val action = SalesDetail
        .filter(_.customerId === customerId.value)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
        .result.headOption
      whenReady(jdbcService.db.run(action)) {
        inside(_) {
          case Some(result) =>
            expect {
              result.walletSettlementId === BigDecimal(7)
              result.walletId === Option(walletId.value)
              result.customerNumber === Option(customerNumber)
              result.dealStatus === Option("00")
              result.specificDealInfo === Option("")
              result.originDealId === Option("originIntranId")
              result.maskingInfo === Option(issuingServicePayCredential.housePan.value)
              result.saleDatetime === Option(Timestamp.valueOf(LocalDateTime.of(2019, Month.JANUARY, 1, 11, 11, 11)))
              result.dealDate === Option(Timestamp.valueOf(date.now().truncatedTo(ChronoUnit.DAYS)))
              result.saleCancelType === Option("02")
              result.amount === Option(issuingServicePaymentRequest1.amountTran.toBigDecimal)
              result.memberStoreId === Option(issuingServicePaymentRequest1.accptrId)
              result.memberStoreName === Option("加盟店名")
              result.cashBackTempAmount === None
              result.applicationExtractFlag === Option(BigDecimal(1))
              result.customerId === Option(customerId.value)
              result.eventPersistenceId === Option(eventPersistenceId)
              result.eventSequenceNumber === Option(BigDecimal(eventSequenceNumber))
            }
        }
      }
    }

    "SalesDetailテーブルに合うデータのcashBackTempAmountがNone" in withJDBC { db =>
      db.prepare(
        SalesDetail += SalesDetailRowSeed.copy(
          walletSettlementId = 1,
          settlementType = Option("01"),
          walletId = Option("12"),
          customerNumber = Option("45"),
          dealStatus = Option("78"),
          specificDealInfo = Option("11"),
          originDealId = Option("11"),
          contractNumber = Option("12"),
          maskingInfo = Option("12"),
          saleDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          dealDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          saleCancelType = Option(SaleCancelType.sale),
          sendDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          authoriNumber = Option(BigDecimal(2)),
          amount = Option(BigDecimal(2)),
          memberStoreId = Option("12"),
          memberStorePosId = Option("12"),
          memberStoreName = Option("12"),
          failureCancelFlag = Option(BigDecimal(2)),
          errorCode = Option("12"),
          dealSerialNumber = Option("12"),
          paymentId = Option("567"),
          originalPaymentId = Option("777"),
          cashBackTempAmount = None,
          cashBackFixedAmount = Option(BigDecimal(2)),
          applicationExtractFlag = Option(BigDecimal(2)),
          customerId = Option("12"),
          saleExtractedFlag = Option(BigDecimal(2)),
          eventPersistenceId = Option(eventPersistenceId),
          insertDate = Timestamp.valueOf(LocalDateTime.now()),
          insertUserId = "12",
          updateDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          updateUserId = Option("12"),
          versionNo = BigDecimal(0),
          logicalDeleteFlag = BigDecimal(0),
        ),
      )
      implicit val traceId: TraceId = TraceId("traceId0001")
      val customerId                = "789"
      val walletId                  = WalletId("123")
      val customerNumber            = "333"
      val issuingServiceResponse = IssuingServiceResponse(
        IntranId(""),
        "123",
        "",
      )
      val issuingServicePayCredential =
        IssuingServicePayCredential(
          Option(walletId),
          Option(customerNumber),
          "",
          None,
          Option("加盟店名"),
          "369",
          HousePan(""),
          TerminalId(""),
        )
      val issuingServiceCancel      = Cancel(ClientId(333), CustomerId(customerId), WalletShopId(""), OrderId(""))
      val settlementSuccessResponse = SettlementSuccessResponse()
      val systemDate                = date.now()
      val saleDateTime              = date.now()

      val event =
        CancelSuccessConfirmed(
          issuingServiceResponse,
          issuingServicePayCredential,
          issuingServiceCancel,
          settlementSuccessResponse,
          IntranId("originIntranId"),
          saleDateTime,
          acquirerReversalRequestParameter,
          issuingServicePaymentRequest1,
          systemDate,
        )

      whenReady(jdbcService.db.run(eventHandler.handle(event).transactionally)) { _ => }

      val action = SalesDetail
        .filter(_.customerId === customerId.value)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
        .result.headOption
      whenReady(jdbcService.db.run(action)) {
        inside(_) {
          case Some(result) =>
            expect {
              result.cashBackTempAmount === None
            }
        }
      }
    }

    "SalesDetailテーブルに合うデータが2件以上cashBackTempAmountがNone" in withJDBC { db =>
      db.prepare(
        SalesDetail += SalesDetailRowSeed.copy(
          walletSettlementId = 1,
          settlementType = Option("01"),
          walletId = Option("12"),
          customerNumber = Option("45"),
          dealStatus = Option("78"),
          specificDealInfo = Option("11"),
          originDealId = Option("11"),
          contractNumber = Option("12"),
          maskingInfo = Option("12"),
          saleDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          dealDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          saleCancelType = Option(SaleCancelType.sale),
          sendDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          authoriNumber = Option(BigDecimal(2)),
          amount = Option(BigDecimal(2)),
          memberStoreId = Option("12"),
          memberStorePosId = Option("12"),
          memberStoreName = Option("12"),
          failureCancelFlag = Option(BigDecimal(2)),
          errorCode = Option("12"),
          dealSerialNumber = Option("12"),
          paymentId = Option("567"),
          originalPaymentId = Option("777"),
          cashBackTempAmount = Option(BigDecimal(5)),
          cashBackFixedAmount = Option(BigDecimal(2)),
          applicationExtractFlag = Option(BigDecimal(2)),
          customerId = Option("12"),
          saleExtractedFlag = Option(BigDecimal(2)),
          eventPersistenceId = Option(eventPersistenceId),
          insertDate = Timestamp.valueOf(LocalDateTime.now()),
          insertUserId = "12",
          updateDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          updateUserId = Option("12"),
          versionNo = BigDecimal(0),
          logicalDeleteFlag = BigDecimal(0),
        ),
        SalesDetail += SalesDetailRowSeed.copy(
          walletSettlementId = 2,
          settlementType = Option("01"),
          walletId = Option("12"),
          customerNumber = Option("45"),
          dealStatus = Option("78"),
          specificDealInfo = Option("11"),
          originDealId = Option("11"),
          contractNumber = Option("12"),
          maskingInfo = Option("12"),
          saleDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          dealDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          saleCancelType = Option(SaleCancelType.sale),
          sendDatetime = Option(Timestamp.valueOf(LocalDateTime.now())),
          authoriNumber = Option(BigDecimal(2)),
          amount = Option(BigDecimal(2)),
          memberStoreId = Option("12"),
          memberStorePosId = Option("12"),
          memberStoreName = Option("12"),
          failureCancelFlag = Option(BigDecimal(2)),
          errorCode = Option("12"),
          dealSerialNumber = Option("12"),
          paymentId = Option("567"),
          originalPaymentId = Option("777"),
          cashBackTempAmount = Option(BigDecimal(2)),
          cashBackFixedAmount = Option(BigDecimal(2)),
          applicationExtractFlag = Option(BigDecimal(2)),
          customerId = Option("12"),
          saleExtractedFlag = Option(BigDecimal(2)),
          eventPersistenceId = Option(eventPersistenceId),
          insertDate = Timestamp.valueOf(LocalDateTime.now()),
          insertUserId = "12",
          updateDate = Option(Timestamp.valueOf(LocalDateTime.now())),
          updateUserId = Option("12"),
          versionNo = BigDecimal(0),
          logicalDeleteFlag = BigDecimal(0),
        ),
      )
      implicit val traceId: TraceId = TraceId("traceId0001")
      val customerId                = "789"
      val walletId                  = WalletId("123")
      val customerNumber            = "333"
      val issuingServiceResponse = IssuingServiceResponse(
        IntranId(""),
        "123",
        "",
      )
      val issuingServicePayCredential =
        IssuingServicePayCredential(
          Option(walletId),
          Option(customerNumber),
          "",
          None,
          Option("加盟店名"),
          "369",
          HousePan(""),
          TerminalId(""),
        )
      val issuingServiceCancel      = Cancel(ClientId(333), CustomerId(customerId), WalletShopId(""), OrderId(""))
      val settlementSuccessResponse = SettlementSuccessResponse()
      val systemDate                = date.now()
      val saleDateTime              = date.now()

      val event =
        CancelSuccessConfirmed(
          issuingServiceResponse,
          issuingServicePayCredential,
          issuingServiceCancel,
          settlementSuccessResponse,
          IntranId("originIntranId"),
          saleDateTime,
          acquirerReversalRequestParameter,
          issuingServicePaymentRequest1,
          systemDate,
        )

      whenReady(jdbcService.db.run(eventHandler.handle(event).transactionally)) { _ => }

      val action = SalesDetail
        .filter(_.customerId === customerId.value)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
        .result.headOption
      whenReady(jdbcService.db.run(action)) {
        inside(_) {
          case Some(result) =>
            expect {
              result.cashBackTempAmount === None
            }
        }
      }
    }

  }

  private def createWalletSettlementIdSeq(): Unit = {
    val tables = diSession.build[Tables]
    import tables.profile.api._
    val action =
      sqlu"DROP SEQUENCE SALES_DETAIL_SEQ ".asTry // エラー無視
        .andThen {
          sqlu"CREATE SEQUENCE SALES_DETAIL_SEQ increment by 1 start with 1"
        }
    whenReady(jdbcService.db.run(action)) { _ =>
      // do nothing
    }
  }

  override def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }
}
