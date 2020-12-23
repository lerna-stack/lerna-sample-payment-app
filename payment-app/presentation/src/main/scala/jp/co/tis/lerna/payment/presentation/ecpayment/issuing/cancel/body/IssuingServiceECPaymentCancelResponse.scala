package jp.co.tis.lerna.payment.presentation.ecpayment.issuing.cancel.body

import jp.co.tis.lerna.payment.adapter.ecpayment.model.{ OrderId, WalletShopId }
import lerna.http.json.AnyValJsonFormat
import spray.json.{ DefaultJsonProtocol, JsonFormat, RootJsonFormat }

final case class IssuingServiceECPaymentCancelResponse(
    walletShopId: WalletShopId,
    orderId: OrderId,
)

object IssuingServiceECPaymentCancelResponse {
  import DefaultJsonProtocol._

  implicit private val orderIdJsonFormat: JsonFormat[OrderId] =
    AnyValJsonFormat(OrderId.apply, OrderId.unapply)

  implicit private val walletShopIdJsonFormat: JsonFormat[WalletShopId] =
    AnyValJsonFormat(WalletShopId.apply, WalletShopId.unapply)

  implicit val jsonFormat: RootJsonFormat[IssuingServiceECPaymentCancelResponse] =
    jsonFormat2(IssuingServiceECPaymentCancelResponse.apply)

}
