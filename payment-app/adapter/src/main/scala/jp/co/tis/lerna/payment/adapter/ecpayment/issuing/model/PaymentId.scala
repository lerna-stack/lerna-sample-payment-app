package jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model

import lerna.util.lang.Equals._

// (会員ごと)決済番号
sealed abstract case class PaymentId(value: String) {
  require(value.length() === 5)
}

object PaymentId {
  def apply(value: BigInt): PaymentId = new PaymentId("%05d".format(value.longValue)) {}

  val maxSequence: BigInt = BigInt("99999")
}
