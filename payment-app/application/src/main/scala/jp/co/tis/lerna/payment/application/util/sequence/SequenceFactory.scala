package jp.co.tis.lerna.payment.application.util.sequence

import akka.actor.typed.ActorSystem
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.{ PaymentId, TransactionId }
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.util.sequence.{ CassandraSequenceFactory, SequenceFactory }
import lerna.util.tenant.Tenant

/**  (会員ごと)決済番号(売上)
  */
trait PaymentIdSequenceFactory extends SequenceFactory {
  final override def maxSequence: BigInt = PaymentId.maxSequence
}

/**  取引ID
  */
trait TransactionIdSequenceFactory extends SequenceFactory {
  final override def maxSequence: BigInt = TransactionId.maxSequence
}

/** 取引ID
  */
class TransactionIdSequenceFactoryImpl(val config: Config, val system: ActorSystem[Nothing])
    extends CassandraSequenceFactory
    with TransactionIdSequenceFactory {

  final override def seqId                  = "SQ02"
  final override def sequenceCacheSize: Int = 3

  override def supportedTenants: Seq[Tenant] = AppTenant.values
}

/** (会員ごと)決済番号
  */
class PaymentIdSequenceFactoryImpl(val config: Config, val system: ActorSystem[Nothing])
    extends CassandraSequenceFactory
    with PaymentIdSequenceFactory {

  final override def seqId                  = "SQ03"
  final override def sequenceCacheSize: Int = 1

  override def supportedTenants: Seq[Tenant] = AppTenant.values
}
