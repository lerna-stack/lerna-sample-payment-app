package jp.co.tis.lerna.payment.application.ecpayment.issuing

import akka.actor.typed.ActorSystem
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.PaymentId
import jp.co.tis.lerna.payment.adapter.wallet.CustomerId
import jp.co.tis.lerna.payment.application.util.sequence.PaymentIdSequenceFactory
import lerna.util.tenant.Tenant

import scala.concurrent.Future

class PaymentIdFactoryImpl(
    factory: PaymentIdSequenceFactory,
    system: ActorSystem[Nothing],
) extends PaymentIdFactory {
  import system.executionContext

  override def generateIdFor(customerId: CustomerId)(implicit tenant: Tenant): Future[PaymentId] =
    factory.nextId(customerId.value).map(PaymentId.apply)

}
