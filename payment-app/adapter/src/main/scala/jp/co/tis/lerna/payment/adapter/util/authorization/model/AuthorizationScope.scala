package jp.co.tis.lerna.payment.adapter.util.authorization.model

final case class AuthorizationScope(value: String) extends AnyVal

object AuthorizationScope {
  val WSettlementWrite = AuthorizationScope("w_settlement_write")
  val OSettlementWrite = AuthorizationScope("o_settlement_write")
}
