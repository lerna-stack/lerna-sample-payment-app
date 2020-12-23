package jp.co.tis.lerna.payment.adapter.issuing.model

import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.IntranId

// 承認売上応答
// 障害取消応答
final case class IssuingServiceResponse(
    intranid: IntranId, // 取引特定情報
    authId: String,     // 承認番号
    rErrcode: String,   //エラーコード
)
