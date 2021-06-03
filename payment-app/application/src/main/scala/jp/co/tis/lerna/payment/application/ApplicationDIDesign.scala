package jp.co.tis.lerna.payment.application

import akka.actor.ActorSystem
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.IssuingServiceECPaymentApplication
import jp.co.tis.lerna.payment.adapter.util.authorization.ClientAuthorizationApplication
import jp.co.tis.lerna.payment.adapter.util.health.HealthCheckApplication
import jp.co.tis.lerna.payment.adapter.util.shutdown.GracefulShutdownApplication
import jp.co.tis.lerna.payment.application.ecpayment.issuing._
import jp.co.tis.lerna.payment.application.util.authorization.ClientAuthorizationApplicationImpl
import jp.co.tis.lerna.payment.application.util.health.HealthCheckApplicationImpl
import jp.co.tis.lerna.payment.application.util.shutdown.GracefulShutdownApplicationImpl
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.management.stats.Metrics
import wvlet.airframe.{ newDesign, Design }

object ApplicationDIDesign extends ApplicationDIDesign

/** Application プロジェクト内のコンポーネントの [[wvlet.airframe.Design]] を定義する
  */
// Airframe が生成するコードを Wartremover が誤検知してしまうため
@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
trait ApplicationDIDesign {

  import jp.co.tis.lerna.payment.application.util.sequence._

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  val applicationDesign: Design = newDesign
    .bind[ClientAuthorizationApplication].to[ClientAuthorizationApplicationImpl]
    .bind[Metrics].toSingletonProvider[ActorSystem] { system =>
      Metrics(system, AppTenant.values.toSet)
    }
    .bind[TransactionIdSequenceFactory].to[TransactionIdSequenceFactoryImpl]
    .bind[PaymentIdSequenceFactory].to[PaymentIdSequenceFactoryImpl]
    .bind[IssuingServiceECPaymentApplication].to[IssuingServiceECPaymentApplicationImpl]
    .bind[TransactionIdFactory].to[TransactionIdFactoryImpl]
    .bind[PaymentIdFactory].to[PaymentIdFactoryImpl]
    .bind[HealthCheckApplication].to[HealthCheckApplicationImpl]
    .bind[GracefulShutdownApplication].to[GracefulShutdownApplicationImpl]
}
