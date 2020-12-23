package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import jp.co.tis.lerna.payment.adapter.ecpayment.model.{ OrderId, WalletShopId }
import jp.co.tis.lerna.payment.adapter.wallet.ClientId
import jp.co.tis.lerna.payment.utility.AppRequestContext
import lerna.util.akka.ReplyTo

final case class ProcessingContext(
    clientId: ClientId,
    walletShopId: WalletShopId,
    orderId: OrderId,
    replyTo: ReplyTo,
)(implicit val appRequestContext: AppRequestContext)
