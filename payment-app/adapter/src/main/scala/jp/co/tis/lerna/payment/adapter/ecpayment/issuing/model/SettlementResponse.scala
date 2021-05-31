package jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model

import jp.co.tis.lerna.payment.adapter.util.OnlineProcessingFailureMessage

sealed trait SettlementResponse

// 成功場合のレスポンス、Actorで作成して、クライアントへ返却
final case class SettlementSuccessResponse() extends SettlementResponse

/** TODO: 整理(application packageでしか使用されていないので adapter に定義する必要がない)
  */
final case class SettlementFailureResponse(message: OnlineProcessingFailureMessage) extends SettlementResponse
