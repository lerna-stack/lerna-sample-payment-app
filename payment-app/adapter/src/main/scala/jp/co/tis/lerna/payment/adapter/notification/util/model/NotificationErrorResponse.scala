package jp.co.tis.lerna.payment.adapter.notification.util.model

final case class NotificationErrorResponse(message: Option[String], errors: Option[Seq[Errors]])
