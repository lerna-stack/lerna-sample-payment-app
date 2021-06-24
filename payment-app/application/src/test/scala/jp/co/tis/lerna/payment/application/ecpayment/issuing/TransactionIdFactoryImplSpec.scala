package jp.co.tis.lerna.payment.application.ecpayment.issuing

import akka.actor.typed.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.TransactionId
import jp.co.tis.lerna.payment.application.util.sequence.TransactionIdSequenceFactory
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
class TransactionIdFactoryImplSpec
    extends ScalaTestWithTypedActorTestKit(ConfigFactory.load("application.conf"))
    with StandardSpec
    with DISessionSupport {

  private val transactionIdSequenceFactory = new TransactionIdSequenceFactory {
    override def nextId(subId: Option[String])(implicit tenant: Tenant): Future[BigInt] = Future.successful(BigInt(1))
  }

  override protected val diDesign: Design = UtilityDIDesign.utilityDesign
    .bind[TransactionIdFactory].to[TransactionIdFactoryImpl]
    .bind[TransactionIdSequenceFactory].toInstance(transactionIdSequenceFactory)
    .bind[ActorSystem[Nothing]].toInstance(system)
    .bind[Config].toInstance(ConfigFactory.load())

  private implicit val tenant: Tenant = Example

  "TransactionIdFactory" when {
    "正常系 12桁の数字が取得できる" in {
      val transactionIdFactory = diSession.build[TransactionIdFactory]
      whenReady(transactionIdFactory.generate) { r =>
        expect { r === TransactionId(1) }
      }
    }
  }
}
