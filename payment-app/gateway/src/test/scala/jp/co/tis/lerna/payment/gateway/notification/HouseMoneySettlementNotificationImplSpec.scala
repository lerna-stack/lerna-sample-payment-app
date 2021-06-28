package jp.co.tis.lerna.payment.gateway.notification

import akka.actor.typed.ActorSystem
import com.github.tomakehurst.wiremock.client.WireMock._
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.adapter.notification.HouseMoneySettlementNotification
import jp.co.tis.lerna.payment.adapter.notification.util.model.{ NotificationFailure, NotificationSuccess }
import jp.co.tis.lerna.payment.gateway.mock.{
  ExternalServiceMockDIDesign,
  NotificationSystemMock,
  NotificationSystemMockSupport,
}
import jp.co.tis.lerna.payment.utility.{ AppRequestContext, UtilityDIDesign }
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.Example
import lerna.testkit.airframe.DISessionSupport
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.util.trace.TraceId
import org.scalatest.Inside
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
class HouseMoneySettlementNotificationImplSpec
    extends ScalaTestWithTypedActorTestKit()
    with StandardSpec
    with DISessionSupport
    with NotificationSystemMockSupport
    with Inside {
  implicit val appRequestContext: AppRequestContext = AppRequestContext(TraceId("1"), tenant = Example)

  override protected val diDesign: Design = UtilityDIDesign.utilityDesign
    .add(ExternalServiceMockDIDesign.externalServiceMockDesign)
    .bind[Config].toProvider { notificationSystemMockSystemMock: NotificationSystemMock =>
      ConfigFactory
        .parseString(s"""
                      | jp.co.tis.lerna.payment.gateway {
                      |  wallet-system.default.base-url = "${notificationSystemMockSystemMock.server.baseUrl}"
                      | }
      """.stripMargin)
        .withFallback(ConfigFactory.defaultReferenceUnresolved())
        .resolve()
    }
    .bind[ActorSystem[Nothing]].toInstance(system)
    .bind[HouseMoneySettlementNotification].to[HouseMoneySettlementNotificationImpl]

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))

  "HouseMoneySettlementNotificationImplSpec" should {
    val notificationSystemGateway: HouseMoneySettlementNotification =
      diSession.build[HouseMoneySettlementNotification]
    "リクエスト成功 response: NotificationSuccess" in {
      notificationSystemMock.importStubs(
        NotificationSystemMock.`決済履歴管理APIで成功を返す`,
      )

      whenReady(notificationSystemGateway.notice("1")) { res =>
        res shouldBe a[NotificationSuccess]
      }

      val except = """{"walletSettlementId":"1"}"""

      notificationSystemMock.server.verify(
        postRequestedFor(urlEqualTo("/housemoney/settlement/notices"))
          .withRequestBody(matchingJsonPath("walletSettlementId", matching("1")))
          .withHeader("Content-Type", equalTo("application/json"))
          .withHeader("Content-Length", equalTo(except.length.toString))
          .withHeader("Accept", equalTo("application/json"))
          .withHeader("Cache-Control", equalTo("no-cache")),
      )
    }
    "リクエスト失敗(400) response: NotificationFailure" in {
      notificationSystemMock.importStubs(
        NotificationSystemMock.`決済履歴管理APIで失敗(400)を返す`,
      )

      whenReady(notificationSystemGateway.notice("1")) { res =>
        res shouldBe a[NotificationFailure]
      }
    }

    "リクエスト失敗(400: Unmarshal error) response: NotificationFailure" in {
      notificationSystemMock.importStubs(
        NotificationSystemMock.`決済履歴管理APIで失敗(json形式でないもの)を返す`,
      )

      whenReady(notificationSystemGateway.notice("1")) { res =>
        res shouldBe a[NotificationFailure]
      }
    }

    "リクエスト失敗(400以外) response: NotificationFailure" in {
      notificationSystemMock.importStubs(
        NotificationSystemMock.`決済履歴管理APIで失敗(500)を返す`,
      )

      whenReady(notificationSystemGateway.notice("1")) { res =>
        res shouldBe a[NotificationFailure]
      }
    }

    "リクエスト失敗(タイムアウト) response: NotificationFailure" in {
      notificationSystemMock.importStubs(
        NotificationSystemMock.`決済履歴管理APIでタイムアウト`,
      )

      diDesign
        .bind[Config].toInstance {
          ConfigFactory
            .parseString(s"""
                          | jp.co.tis.lerna.payment.gateway {
                          |  wallet-system.default.base-url = "http://localhost:0"
                          | }
      """.stripMargin)
            .withFallback(ConfigFactory.defaultReferenceUnresolved())
            .resolve()
        }

      val badNotificationSystemGateway = diDesign.newSession.build[HouseMoneySettlementNotification]

      whenReady(badNotificationSystemGateway.notice("1")) { res =>
        res shouldBe a[NotificationFailure]
      }
    }
  }
}
