package jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model

import jp.co.tis.lerna.payment.adapter.ecpayment.model.{ OrderId, WalletShopId }
import jp.co.tis.lerna.payment.adapter.wallet.{ ClientId, CustomerId }

// Presentation -> Application パラメータ引き渡す用
final case class PaymentCancelParameter(
    walletShopId: WalletShopId,
    orderId: OrderId,
    customerId: CustomerId,
    clientId: ClientId,
)
