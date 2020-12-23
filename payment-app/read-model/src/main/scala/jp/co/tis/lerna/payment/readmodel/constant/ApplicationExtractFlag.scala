package jp.co.tis.lerna.payment.readmodel.constant

/** アプリ連携フラグ
  */
object ApplicationExtractFlag {

  /** 取得対象外
    * 0
    */
  val nonExtractable = BigDecimal(0)

  /** 取得対象
    * 1
    */
  val extractable = BigDecimal(1)
}
