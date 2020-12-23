package jp.co.tis.lerna.payment.adapter.issuing.model

import java.time.LocalDateTime

import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._

final case class AuthorizationRequestParameter(
    pan: HousePan,                // カード番号
    amountTran: AmountTran,       // 取引金額
    tranDateTime: LocalDateTime,  // 送信日時
    transactionId: TransactionId, // 取引ID
    accptrId: String,             // 加盟店ID
    paymentId: PaymentId,         // (会員ごと)決済番号
    terminalId: TerminalId,       // 端末識別番号
) {
  val MessageTypeIndicator: MTI = ShoninUriage
}
