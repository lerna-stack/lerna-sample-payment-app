package jp.co.tis.lerna.payment.presentation.util.errorhandling

final case class ErrorMessage(message: String, code: String)

final case class ErrorInfo(resource: String, field: String, code: String)
