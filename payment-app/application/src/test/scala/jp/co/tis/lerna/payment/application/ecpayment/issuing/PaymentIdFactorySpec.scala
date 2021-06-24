package jp.co.tis.lerna.payment.application.ecpayment.issuing

import akka.actor.typed.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.PaymentId
import jp.co.tis.lerna.payment.adapter.wallet.CustomerId
import jp.co.tis.lerna.payment.application.util.sequence.PaymentIdSequenceFactory
import jp.co.tis.lerna.payment.utility.UtilityDIDesign
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.Example
import lerna.testkit.airframe.DISessionSupport
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.util.tenant.Tenant
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
class PaymentIdFactorySpec
    extends ScalaTestWithTypedActorTestKit(ConfigFactory.load("application.conf"))
    with StandardSpec
    with DISessionSupport {

  final case object PaymentIdSequenceFactoryImpl extends PaymentIdSequenceFactory {
    override def nextId(subId: Option[String])(implicit tenant: Tenant): Future[BigInt] = subId match {
      case Some(id) =>
        Future.successful(BigInt(id))
      case None =>
        Future.failed(new IllegalArgumentException)
    }
  }

  override protected val diDesign: Design = UtilityDIDesign.utilityDesign
    .bind[PaymentIdFactory].to[PaymentIdFactoryImpl]
    .bind[PaymentIdSequenceFactory].toInstance(PaymentIdSequenceFactoryImpl)
    .bind[ActorSystem[Nothing]].toInstance(system)
    .bind[Config].toInstance(ConfigFactory.load())

  private implicit val tenant: Tenant = Example

  "PaymentIdFactory" when {
    "正常系 5桁の数字が取得できる" in {
      val paymentIdFactory = diSession.build[PaymentIdFactory]
      whenReady(paymentIdFactory.generateIdFor(CustomerId("00006666"))) { r =>
        expect { r === PaymentId(6666) }
      }
    }
  }
}
