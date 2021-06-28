package jp.co.tis.lerna.payment.application.ecpayment.issuing

import akka.actor.typed.ActorSystem
import akka.cluster.Cluster
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.IssuingServiceECPaymentApplication
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._
import jp.co.tis.lerna.payment.adapter.ecpayment.model.{ OrderId, WalletShopId }
import jp.co.tis.lerna.payment.adapter.issuing.IssuingServiceGateway
import jp.co.tis.lerna.payment.adapter.issuing.model.{
  AcquirerReversalRequestParameter,
  AuthorizationRequestParameter,
  IssuingServiceResponse,
}
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.adapter.wallet.{ ClientId, CustomerId }
import jp.co.tis.lerna.payment.readmodel.{ JDBCSupport, ReadModelDIDesign }
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.Example
import jp.co.tis.lerna.payment.utility.{ AppRequestContext, UtilityDIDesign }
import lerna.testkit.airframe.DISessionSupport
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.util.tenant.Tenant
import lerna.util.trace.TraceId
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.Inside
import wvlet.airframe.Design

import scala.concurrent.Future
import org.scalatest.wordspec.AnyWordSpecLike

object IssuingServiceGatewayECPaymentApplicationSpec {
  private val issuingService = new IssuingServiceGateway {
    override def requestAuthorization(parameter: AuthorizationRequestParameter)(implicit
        appRequestContext: AppRequestContext,
    ): Future[IssuingServiceResponse] = ???

    override def requestAcquirerReversal(
        parameter: AcquirerReversalRequestParameter,
        originalRequestParameter: AuthorizationRequestParameter,
    )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] = ???
  }
  private val transactionIdFactory = new TransactionIdFactory {
    override def generate()(implicit tenant: Tenant): Future[TransactionId] =
      Future.successful(TransactionId(123456))
  }
  private val paymentIdFactory = new PaymentIdFactory {
    override def generateIdFor(customerId: CustomerId)(implicit tenant: Tenant): Future[PaymentId] =
      Future.successful(PaymentId(12345))
  }

  private val config = ConfigFactory
    .parseString(s"""
                    | akka {
                    |  actor {
                    |    provider = "cluster"
                    |  }
                    |  cluster.sharding.passivate-idle-entity-after = off
                    |
                    |  remote {
                    |    artery {
                    |      canonical {
                    |        port = 0
                    |      }
                    |    }
                    |  }
                    |}
       """.stripMargin).withFallback(ConfigFactory.load("application-test.conf"))
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
class IssuingServiceGatewayECPaymentApplicationSpec
    extends ScalaTestWithTypedActorTestKit(IssuingServiceGatewayECPaymentApplicationSpec.config)
    with StandardSpec
    with AnyWordSpecLike
    with DISessionSupport
    with JDBCSupport
    with Inside {
  import IssuingServiceGatewayECPaymentApplicationSpec._

  implicit val appRequestContext: AppRequestContext = AppRequestContext(TraceId("1"), tenant = Example)

  override protected val diDesign: Design = UtilityDIDesign.utilityDesign
    .add(ReadModelDIDesign.readModelDesign)
    .bind[ActorSystem[Nothing]].toInstance(system)
    .bind[IssuingServiceGateway].toInstance(issuingService)
    .bind[TransactionIdFactory].toInstance(transactionIdFactory)
    .bind[PaymentIdFactory].toInstance(paymentIdFactory)
    .bind[Config].toInstance {
      config
    }
    .bind[IssuingServiceECPaymentApplication].to[IssuingServiceECPaymentApplicationImpl]

  "IssuingServiceECPaymentApplicationSpec" should {
    val system  = diSession.build[ActorSystem[Nothing]]
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)

    val application = diSession.build[IssuingServiceECPaymentApplication]

    "異常系(未初期化Actorに対してメッセージ決済要求送信) response: messageId = CODE-002" in {

      val settlementConfirmResponse =
        PaymentParameter(
          AmountTran(100),
          WalletShopId("123"),
          OrderId("123"),
          CustomerId("123"),
          ClientId(123),
        )

      inside(application.pay(settlementConfirmResponse).failed.futureValue(timeout(Span(10, Seconds)))) {
        case ex: BusinessException =>
          expect { ex.message.messageId === "CODE-002" }
      }
    }

    "異常系(未初期化Actorに対してメッセージ取消要求送信) response: messageId = CODE-002" in {

      val paymentCancelParameter =
        PaymentCancelParameter(WalletShopId("456"), OrderId("456"), CustomerId("123"), ClientId(123))

      inside(application.cancel(paymentCancelParameter).failed.futureValue(timeout(Span(10, Seconds)))) {
        case ex: BusinessException =>
          expect { ex.message.messageId === "CODE-002" }
      }
      system.terminate()
    }
  }
}
