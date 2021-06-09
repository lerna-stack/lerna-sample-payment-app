package jp.co.tis.lerna.payment.presentation.ecpayment.issuing.payment

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{ ContentTypes, StatusCodes }
import akka.http.scaladsl.server.Directives.provide
import akka.http.scaladsl.server.{ Directive1, MalformedRequestContentRejection }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.IssuingServiceECPaymentApplication
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.{
  PaymentCancelParameter,
  PaymentParameter,
  SettlementSuccessResponse,
}
import jp.co.tis.lerna.payment.adapter.util.authorization.model.AuthorizationScope
import jp.co.tis.lerna.payment.adapter.wallet.{ ClientId, CustomerId }
import jp.co.tis.lerna.payment.presentation.ecpayment.issuing.IssuingService
import jp.co.tis.lerna.payment.presentation.util.api.{ ApiThrottling, NoLimitApiThrottling }
import jp.co.tis.lerna.payment.presentation.util.directives.AuthorizationHeaderDirective
import jp.co.tis.lerna.payment.presentation.util.directives.validation.ValidationJsonSupport
import jp.co.tis.lerna.payment.utility.tenant.Example
import jp.co.tis.lerna.payment.utility.{ AppRequestContext, UtilityDIDesign }
import lerna.testkit.airframe.DISessionSupport
import lerna.util.trace.TraceId
import org.scalatest.Inside
import wvlet.airframe.Design

import scala.concurrent.Future
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object IssuingServiceGatewayPaymentCancelSpec {
  private val issuingServiceECPaymentApplication = new IssuingServiceECPaymentApplication {
    override def pay(
        paymentParameter: PaymentParameter,
    )(implicit appRequestContext: AppRequestContext): Future[SettlementSuccessResponse] = {
      Future.successful(
        SettlementSuccessResponse(
        ),
      )
    }
    override def cancel(
        paymentCancelParameter: PaymentCancelParameter,
    )(implicit appRequestContext: AppRequestContext): Future[SettlementSuccessResponse] = {
      Future.successful(
        SettlementSuccessResponse(
        ),
      )
    }
  }

  class AuthorizationHeaderDirectiveMock() extends AuthorizationHeaderDirective {
    override def authorize(
        scopes: Seq[AuthorizationScope],
    )(implicit appRequestContext: AppRequestContext): Directive1[(CustomerId, ClientId)] = {
      import AuthorizationScope.OSettlementWrite
      require(scopes.contains(OSettlementWrite), s"scopes($scopes) に ${OSettlementWrite.value} が入っていません")
      provide(
        (
          CustomerId("1"),
          ClientId(1),
        ),
      )

    }
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
class IssuingServiceGatewayPaymentCancelSpec
    extends AnyWordSpec
    with Matchers
    with ScalatestRouteTest
    with ValidationJsonSupport
    with Inside
    with DISessionSupport {
  import IssuingServiceGatewayPaymentCancelSpec._

  override val diDesign: Design = UtilityDIDesign.utilityDesign
    .bind[ActorSystem].toInstance(system)
    .bind[ApiThrottling].to[NoLimitApiThrottling]
    .bind[IssuingServiceECPaymentApplication].toInstance(issuingServiceECPaymentApplication)
    .bind[IssuingService].toSingleton
    .bind[Config].toInstance(ConfigFactory.load)
    .bind[AuthorizationHeaderDirective].to[AuthorizationHeaderDirectiveMock]

  implicit val appRequestContext: AppRequestContext = AppRequestContext(TraceId("1"), tenant = Example)

  "server endpoint" when {
    val settlement: IssuingService = diSession.build[IssuingService]

    "IS payment Cancel request" should {
      "IS: 正常系" in {
        val request =
          Put("/00/ec/settlements/12345678901234567890123456789012345678-a/09876543210987654321123456b/cancel")
            .withEntity(
              ContentTypes.`application/json`,
              "",
            )

        request ~> settlement.route() ~> check {
          val expectMessage1 =
            """orderId":"09876543210987654321123456b","walletShopId":"12345678901234567890123456789012345678-a"""
          status should be(StatusCodes.OK)
          contentType should ===(`application/json`)
          responseAs[String] should ===(s"""{"$expectMessage1"}""")
        }
      }

      "IS: 異常系(walletShopId が最大値より長い)" in {
        val request =
          Put("/00/ec/settlements/12345678901234567890123456789012345678901/56789/cancel").withEntity(
            ContentTypes.`application/json`,
            "",
          )

        request ~> settlement.route() ~> check {
          val expectMessage1 =
            """{"code":"CODE-003","message":"walletShopId length with value \"41\" got 41, expected 40 or less"}"""
          status should be(StatusCodes.BadRequest)
          contentType should ===(`application/json`)
          responseAs[String] should ===(s"""$expectMessage1""")
        }
      }

      "IS: 異常系(OrderId が最大値より長い)" in {
        val request =
          Put("/00/ec/settlements/1234567890123456789012345678901234567890/123456789012345678901234567a/cancel")
            .withEntity(
              ContentTypes.`application/json`,
              "",
            )

        request ~> settlement.route() ~> check {
          val expectMessage1 =
            """{"code":"CODE-003","message":"orderId length with value \"28\" got 28, expected 27 or less"}"""
          status should be(StatusCodes.BadRequest)
          contentType should ===(`application/json`)
          responseAs[String] should ===(s"""$expectMessage1""")
        }
      }

      "IS: 異常系(orderId が空)" in {
        val request =
          Put("/00/ec/settlements/12412/cancel").withEntity(
            ContentTypes.`application/json`,
            "",
          )

        request ~> settlement.route() ~> check {
          rejections.map { rejection =>
            rejection shouldBe a[MalformedRequestContentRejection]
          }
        }
      }
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}
