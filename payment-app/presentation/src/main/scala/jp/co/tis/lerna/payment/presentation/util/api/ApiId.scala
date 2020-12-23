package jp.co.tis.lerna.payment.presentation.util.api

object ApiId extends Enumeration {
  type ApiId = Value

  private[util] val BASE: ApiId = Value

  // 各API ID
  // 追加したら、 payment-app/presentation/src/main/resources/reference.conf も編集必要
  // （忘れたら jp.co.tis.lerna.payment.presentation.util.api.ConfigCheck が fail）
  val IssuingService: ApiId = Value
}
