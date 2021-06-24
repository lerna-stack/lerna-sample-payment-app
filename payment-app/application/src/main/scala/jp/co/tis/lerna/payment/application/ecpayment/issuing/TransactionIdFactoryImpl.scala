package jp.co.tis.lerna.payment.application.ecpayment.issuing

import akka.actor.typed.ActorSystem
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.TransactionId
import jp.co.tis.lerna.payment.application.util.sequence.TransactionIdSequenceFactory
import lerna.util.tenant.Tenant

import scala.concurrent.Future

class TransactionIdFactoryImpl(
    factory: TransactionIdSequenceFactory,
    system: ActorSystem[Nothing],
) extends TransactionIdFactory {
  import system.executionContext

  override def generate()(implicit tenant: Tenant): Future[TransactionId] =
    factory.nextId().map(TransactionId.apply)

}
