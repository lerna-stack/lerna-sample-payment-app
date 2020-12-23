package jp.co.tis.lerna.payment.adapter.notification

import jp.co.tis.lerna.payment.adapter.notification.util.model.NotificationResponse
import jp.co.tis.lerna.payment.adapter.util.gateway.InternalGateway
import jp.co.tis.lerna.payment.utility.AppRequestContext

import scala.concurrent.Future

trait HouseMoneySettlementNotification extends InternalGateway {
  def notice(walletSettlementId: String)(implicit appRequestContext: AppRequestContext): Future[NotificationResponse]
}
