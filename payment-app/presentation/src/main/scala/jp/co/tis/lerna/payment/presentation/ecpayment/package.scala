package jp.co.tis.lerna.payment.presentation

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import jp.co.tis.lerna.payment.adapter.ecpayment.model.{ OrderId, WalletShopId }
import jp.co.tis.lerna.payment.presentation.ecpayment.RequestValidator.{ orderIdValidator, walletShopIdValidator }
import jp.co.tis.lerna.payment.presentation.util.directives.validation.ValidationDirectives

package object ecpayment extends ValidationDirectives {
  val pathPrefixWalletShopId: Directive1[WalletShopId] =
    for {
      rawWalletShopId <- pathPrefix(Segment)
      _               <- valid(rawWalletShopId, walletShopIdValidator)
    } yield WalletShopId(rawWalletShopId)

  val pathPrefixOrderId: Directive1[OrderId] =
    for {
      rawOrderId <- pathPrefix(Segment)
      _          <- valid(rawOrderId, orderIdValidator)
    } yield OrderId(rawOrderId)
}
