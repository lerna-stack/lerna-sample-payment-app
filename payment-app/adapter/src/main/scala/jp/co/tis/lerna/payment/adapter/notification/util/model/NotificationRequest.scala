package jp.co.tis.lerna.payment.adapter.notification.util.model

sealed trait NotificationRequest

// Issuing Service 決済
final case class HouseMoneySettlementNotificationRequest(walletSettlementId: String) extends NotificationRequest
