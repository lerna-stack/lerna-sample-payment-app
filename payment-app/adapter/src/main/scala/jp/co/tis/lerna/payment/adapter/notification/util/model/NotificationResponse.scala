package jp.co.tis.lerna.payment.adapter.notification.util.model

sealed trait NotificationResponse
final case class NotificationSuccess() extends NotificationResponse
final case class NotificationFailure() extends NotificationResponse
