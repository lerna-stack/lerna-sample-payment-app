package jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model

import lerna.util.lang.Equals._

// 取引ID
sealed abstract case class TransactionId(value: String) {
  require(value.length() === 12)
}

object TransactionId {
  def apply(value: BigInt): TransactionId = new TransactionId("%012d".format(value.longValue)) {}

  val maxSequence: BigInt = BigInt("999999999999")
}
