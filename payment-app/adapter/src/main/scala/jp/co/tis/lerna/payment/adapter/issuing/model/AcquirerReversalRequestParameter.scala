package jp.co.tis.lerna.payment.adapter.issuing.model

import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._

final case class AcquirerReversalRequestParameter(
    transactionId: TransactionId, // 取引ID
    paymentId: PaymentId,         // (会員ごと)決済番号
    terminalId: TerminalId,       // 端末識別番号
) {
  val MessageTypeIndicator: MTI = ShoninTorikesi
}
