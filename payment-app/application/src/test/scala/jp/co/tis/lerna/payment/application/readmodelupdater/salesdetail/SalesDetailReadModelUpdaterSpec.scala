package jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail

import jp.co.tis.lerna.payment.adapter.notification.HouseMoneySettlementNotification
import jp.co.tis.lerna.payment.adapter.notification.util.model.NotificationResponse
import jp.co.tis.lerna.payment.utility.AppRequestContext

import scala.concurrent.Future

class HouseMoneySettlementNotificationMock extends HouseMoneySettlementNotification {
  override def notice(walletSettlementId: String)(implicit
      appRequestContext: AppRequestContext,
  ): Future[NotificationResponse] = ???
}
