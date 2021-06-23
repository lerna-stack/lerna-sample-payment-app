package jp.co.tis.lerna.payment.gateway.issuing

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._
import jp.co.tis.lerna.payment.adapter.issuing.IssuingServiceGateway
import jp.co.tis.lerna.payment.adapter.issuing.model.{
  AcquirerReversalRequestParameter,
  AuthorizationRequestParameter,
  IssuingServiceResponse,
}
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.gateway.mock.{
  ExternalServiceMockDIDesign,
  IssuingServiceMock,
  IssuingServiceMockSupport,
}
import jp.co.tis.lerna.payment.utility.{ AppRequestContext, UtilityDIDesign }
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.Example
import lerna.testkit.airframe.DISessionSupport
import lerna.util.trace.TraceId
import org.scalatest.Inside
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import wvlet.airframe.Design

// Lint回避のため
@SuppressWarnings(
  Array(
    "lerna.warts.Awaits",
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
class IssuingServiceGatewaySpec
    extends TestKit(ActorSystem("IssuingServiceSpec"))
    with StandardSpec
    with DISessionSupport
    with IssuingServiceMockSupport
    with ScalaFutures
    with Inside {

  private implicit val appRequestContext: AppRequestContext = AppRequestContext(TraceId("1"), tenant = Example)

  override protected val diDesign: Design = UtilityDIDesign.utilityDesign
    .add(ExternalServiceMockDIDesign.externalServiceMockDesign)
    .bind[Config].toProvider { serviceMock: IssuingServiceMock =>
      ConfigFactory
        .parseString(s"""
           | jp.co.tis.lerna.payment.gateway {
           |   issuing.default.base-url = "${serviceMock.server.baseUrl}"
           | }
      """.stripMargin)
        .withFallback(ConfigFactory.defaultReferenceUnresolved())
        .resolve()
    }
    .bind[ActorSystem].toInstance(system)
    .bind[IssuingServiceGateway].to[IssuingServiceGatewayImpl]

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  "IssuingService" should {
    val gateway = diSession.build[IssuingServiceGateway]

    val req = AuthorizationRequestParameter(
      pan = HousePan("159"),
      amountTran = AmountTran(174),
      tranDateTime = LocalDateTime.now(),
      transactionId = TransactionId(489),
      accptrId = "567",
      paymentId = PaymentId(498),
      terminalId = TerminalId("123"),
    )

    "承認売上送信成功" in {
      serviceMock.importStubs(
        IssuingServiceMock.`IS:承認売上要求で成功を返す`,
      )

      whenReady(gateway.requestAuthorization(req)) { res: IssuingServiceResponse =>
        expect {
          res.intranid === IntranId("471")
        }
      }
    }

    "承認売上送信失敗" in {
      serviceMock.importStubs(
        IssuingServiceMock.`IS:承認売上要求／承認取消要求でBadRequestを返す`,
      )

      whenReady(gateway.requestAuthorization(req).failed) {
        case ex: BusinessException =>
          expect(ex.message.messageId === "CODE-007")
          expect(ex.message.messageContent.contains("承認売上送信の際に、HTTPヘッダの内容が不正です。"))
      }
    }

    "承認売上送信応答のステータスコードがその他(451)" in {
      serviceMock.importStubs(
        IssuingServiceMock.`IS:承認売上要求でその他のエラーレスポンス(451)`,
      )

      whenReady(gateway.requestAuthorization(req).failed) {
        case ex: BusinessException =>
          expect(ex.message.messageId === "CODE-009")
          expect(ex.message.messageContent.contains("承認売上送信の際に、Issuing Service でのエラーが検知されました。エラーコード：-"))
      }
    }

    "承認売上送信応答のステータスが「500～599」場合、障害取消要求成功" in {
      serviceMock.importStubs(
        IssuingServiceMock.`IS:承認売上要求でServerErrorを返す(500)`,
        IssuingServiceMock.`IS:承認売上に対する障害取消要求で成功を返す`,
      )

      whenReady(gateway.requestAuthorization(req).failed) {
        case ex: BusinessException =>
          expect(ex.message.messageId === "CODE-008")
          expect(ex.message.messageContent.contains("障害取消送信の際に、業務処理にて障害取消が必要なエラーが検知されました。"))
      }
    }

    "承認売上送信応答のステータスが「503」場合" in {
      serviceMock.importStubs(
        IssuingServiceMock.`IS:承認売上要求でServiceUnavailableを返す(503)`,
        IssuingServiceMock.`IS:承認売上に対する障害取消要求で成功を返す`,
      )

      whenReady(gateway.requestAuthorization(req).failed) {
        case ex: BusinessException =>
          expect(ex.message.messageId === "CODE-008")
          expect(ex.message.messageContent.contains("障害取消送信の際に、業務処理にて障害取消が必要なエラーが検知されました。"))
      }
    }

    "承認売上送信応答のステータスが「400」場合、障害取消要求失敗" in {
      serviceMock.importStubs(
        IssuingServiceMock.`IS:承認売上要求でServerErrorを返す(500)`,
        IssuingServiceMock.`IS:承認売上に対する障害取消要求でBadRequestを返す(400)`,
      )

      whenReady(gateway.requestAuthorization(req).failed) {
        case ex: BusinessException =>
          expect(ex.message.messageId === "CODE-007")
          expect(ex.message.messageContent.contains("障害取消送信の際に、HTTPヘッダの内容が不正です。"))
      }
    }

    "承認売上送信応答のステータスが「500～599」場合、障害取消要求応答のステータスが503" in {
      serviceMock.importStubs(
        IssuingServiceMock.`IS:承認売上要求でServerErrorを返す(500)`,
        IssuingServiceMock.`IS:承認売上に対する障害取消要求でServiceUnavailableを返す(503)`,
      )

      whenReady(gateway.requestAuthorization(req).failed) {
        case ex: BusinessException =>
          expect(ex.message.messageId === "CODE-009")
          expect(ex.message.messageContent.contains("障害取消送信の際に、Issuing Service でのエラーが検知されました。"))
      }
    }

    "承認売上送信応答のステータスが「500～599」場合、障害取消要求応答のステータスが500" in {
      serviceMock.importStubs(
        IssuingServiceMock.`IS:承認売上要求でServerErrorを返す(500)`,
        IssuingServiceMock.`IS:承認売上に対する障害取消要求でServerErrorを返す(500)`,
      )

      whenReady(gateway.requestAuthorization(req).failed) {
        case ex: BusinessException =>
          expect(ex.message.messageId === "CODE-009")
          expect(ex.message.messageContent.contains("障害取消送信の際に、Issuing Service でのエラーが検知されました。"))
      }
    }

    "承認売上送信応答のステータスが「500～599」場合、障害取消要求のステータスが451" in {
      serviceMock.importStubs(
        IssuingServiceMock.`IS:承認売上要求でServerErrorを返す(500)`,
        IssuingServiceMock.`IS:承認売上に対する障害取消要求でその他のエラーを返す(451)`,
      )

      whenReady(gateway.requestAuthorization(req).failed) {
        case ex: BusinessException =>
          expect(ex.message.messageId === "CODE-009")
          expect(ex.message.messageContent.contains("障害取消送信の際に、Issuing Service でのエラーが検知されました。"))
      }
    }

    "承認取消送信失敗" in {
      val originalRequest = AuthorizationRequestParameter(
        pan = HousePan("159"),
        amountTran = AmountTran(174),
        tranDateTime = LocalDateTime.now(),
        transactionId = TransactionId(489),
        accptrId = "567",
        paymentId = PaymentId(498),
        terminalId = TerminalId("123"),
      )

      val acquirerReversalRequestParameter = AcquirerReversalRequestParameter(
        transactionId = TransactionId(489),
        paymentId = PaymentId(498),
        terminalId = TerminalId("123"),
      )

      serviceMock.importStubs(
        IssuingServiceMock.`IS:承認売上要求／承認取消要求でBadRequestを返す`,
      )

      whenReady(gateway.requestAcquirerReversal(acquirerReversalRequestParameter, originalRequest).failed) {
        case ex: BusinessException =>
          expect(ex.message.messageId === "CODE-007")
          expect(ex.message.messageContent.contains("承認取消送信の際に、HTTPヘッダの内容が不正です。"))
      }
    }

    "決済失敗 障害取消 接続タイムアウト" in {
      val newSession = diDesign
        .bind[Config].toInstance {
          ConfigFactory
            .parseString(s"""
               | jp.co.tis.lerna.payment.gateway {
               |   issuing.default.base-url = "${serviceMock.server.baseUrl}"
               |   issuing.default.response-timeout = 500 ms
               | }
      """.stripMargin)
            .withFallback(ConfigFactory.defaultReferenceUnresolved())
            .resolve()
        }.newSession

      newSession.start
      val newGateway = newSession.build[IssuingServiceGateway]

      val originalRequest = AuthorizationRequestParameter(
        pan = HousePan("159"),
        amountTran = AmountTran(174),
        tranDateTime = LocalDateTime.now(),
        transactionId = TransactionId(489),
        accptrId = "567",
        paymentId = PaymentId(498),
        terminalId = TerminalId("123"),
      )

      val acquirerReversalRequestParameter = AcquirerReversalRequestParameter(
        transactionId = TransactionId(489),
        paymentId = PaymentId(498),
        terminalId = TerminalId("123"),
      )

      serviceMock.importStubs(
        IssuingServiceMock.`IS:タイムアウトさせる`,
      )

      whenReady(newGateway.requestAcquirerReversal(acquirerReversalRequestParameter, originalRequest).failed) {
        case ex: BusinessException =>
          expect(ex.message.messageId === "CODE-005")
          expect(ex.message.messageContent.contains("障害取消送信でタイムアウトが発生しました。処理を中断します。"))
        case _ =>
          expect(false)
      }
    }

    "決済失敗 接続エラー" in {
      val newSession = diDesign
        .bind[Config].toInstance {
          ConfigFactory
            .parseString(s"""
               | jp.co.tis.lerna.payment.gateway {
               |  issuing.default.base-url = "http://localhost:0/api/v2"
               |  issuing.default.response-timeout = 500 ms
               | }
      """.stripMargin)
            .withFallback(ConfigFactory.defaultReferenceUnresolved())
            .resolve()
        }.newSession

      newSession.start
      val newGateway = newSession.build[IssuingServiceGateway]

      val originalRequest = AuthorizationRequestParameter(
        pan = HousePan("159"),
        amountTran = AmountTran(174),
        tranDateTime = LocalDateTime.now(),
        transactionId = TransactionId(489),
        accptrId = "567",
        paymentId = PaymentId(498),
        terminalId = TerminalId("123"),
      )

      val acquirerReversalRequestParameter = AcquirerReversalRequestParameter(
        transactionId = TransactionId(489),
        paymentId = PaymentId(498),
        terminalId = TerminalId("123"),
      )

      whenReady(newGateway.requestAcquirerReversal(acquirerReversalRequestParameter, originalRequest).failed) {
        _ shouldBe a[BusinessException]
      }
    }
  }

  override def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }
}
