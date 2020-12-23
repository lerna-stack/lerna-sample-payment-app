package jp.co.tis.lerna.payment.presentation.ecpayment.issuing.payment.body

import jp.co.tis.lerna.payment.adapter.ecpayment.model.{ OrderId, WalletShopId }
import lerna.http.json.AnyValJsonFormat
import spray.json.{ DefaultJsonProtocol, JsonFormat, RootJsonFormat }

final case class IssuingServiceECPaymentResponse(
    walletShopId: WalletShopId,
    orderId: OrderId,
)

object IssuingServiceECPaymentResponse {
  import DefaultJsonProtocol._

  implicit private val orderIdJsonFormat: JsonFormat[OrderId] =
    AnyValJsonFormat(OrderId.apply, OrderId.unapply)

  implicit private val walletShopIdJsonFormat: JsonFormat[WalletShopId] =
    AnyValJsonFormat(WalletShopId.apply, WalletShopId.unapply)

  implicit val jsonFormat: RootJsonFormat[IssuingServiceECPaymentResponse] =
    jsonFormat2(IssuingServiceECPaymentResponse.apply)

}
