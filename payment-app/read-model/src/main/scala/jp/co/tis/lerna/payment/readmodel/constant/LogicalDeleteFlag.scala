package jp.co.tis.lerna.payment.readmodel.constant

/** 論理削除フラグ
  */
object LogicalDeleteFlag {

  /** 未削除(有効)
    * 0
    */
  val unDeleted: BigDecimal = BigDecimal(0)

  /** 論理削除済み
    * 1
    */
  val deleted: BigDecimal = BigDecimal(1)
}
