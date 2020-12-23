package jp.co.tis.lerna.payment.gateway.issuing.body

import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._
import lerna.http.json.AnyValJsonFormat
import spray.json.{ deserializationError, DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat }

final case class Request(
    mti: MTI,                             // メッセージタイプ(MTI)
    pan: HousePan,                        // カード番号
    amountTran: AmountTran,               // 取引金額
    tranDateTime: String,                 // 送信日時
    transactionId: TransactionId,         // 取引ID
    accptrId: String,                     // 加盟店ID
    paymentId: PaymentId,                 // (会員ごと)決済番号
    originalPaymentId: Option[PaymentId], // 元取引(会員ごと)決済番号、承認取消の場合のみ
    terminalId: TerminalId,               // 端末識別番号
)

object Request {
  import DefaultJsonProtocol._

  implicit private object MTIJsonFormat extends RootJsonFormat[MTI] {
    override def write(mti: MTI): JsValue = JsString(mti.code)

    override def read(json: JsValue): MTI = json match {
      case x => deserializationError(s"read should not be used.")
    }
  }

  implicit private object AmountTranJsonFormat extends RootJsonFormat[AmountTran] {
    override def write(amountTran: AmountTran): JsValue = {
      // 12桁0埋め
      val formattedString = "%012d".format(amountTran.value)
      JsString(formattedString)
    }
    override def read(json: JsValue): AmountTran = ???
  }

  implicit private object TerminalIdJsonFormat extends RootJsonFormat[TerminalId] {
    override def write(terminalId: TerminalId): JsValue = JsString(terminalId.value)
    override def read(json: JsValue): TerminalId        = ???
  }

  implicit private val housePanJsonFormat: JsonFormat[HousePan] = AnyValJsonFormat(HousePan.apply, HousePan.unapply)

  implicit private object TransactionIdJsonFormat extends RootJsonFormat[TransactionId] {
    override def write(transactionId: TransactionId): JsValue = JsString(transactionId.value)
    override def read(json: JsValue): TransactionId           = ???
  }

  implicit private object PaymentIdJsonFormat extends RootJsonFormat[PaymentId] {
    override def write(paymentId: PaymentId): JsValue = JsString(paymentId.value)
    override def read(json: JsValue): PaymentId       = ???
  }

  implicit val paymentRequestFormat: RootJsonFormat[Request] = jsonFormat9(Request.apply)
}
