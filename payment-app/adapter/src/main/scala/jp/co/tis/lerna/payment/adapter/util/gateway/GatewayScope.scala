package jp.co.tis.lerna.payment.adapter.util.gateway

sealed trait GatewayScope {
  val scope: String
}

trait InternalGateway extends GatewayScope {
  override val scope: String = "internal"
}

trait ExternalGateway extends GatewayScope {
  override val scope: String = "external"
}
