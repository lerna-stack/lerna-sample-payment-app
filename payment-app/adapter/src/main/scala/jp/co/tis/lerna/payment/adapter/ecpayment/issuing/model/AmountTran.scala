package jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model

// 取引金額
final case class AmountTran(value: Long) extends AnyVal {

  def toBigDecimal: BigDecimal = BigDecimal(value)
}
