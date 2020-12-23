package jp.co.tis.lerna.payment.gateway

import jp.co.tis.lerna.payment.adapter.notification.HouseMoneySettlementNotification
import jp.co.tis.lerna.payment.adapter.issuing.IssuingServiceGateway
import jp.co.tis.lerna.payment.gateway.notification.HouseMoneySettlementNotificationImpl
import jp.co.tis.lerna.payment.gateway.issuing.IssuingServiceGatewayImpl
import wvlet.airframe.{ newDesign, Design }

object GatewayDIDesign extends GatewayDIDesign

/** ReadModel プロジェクト内のコンポーネントの [[wvlet.airframe.Design]] を定義する
  */
// Airframe が生成するコードを Wartremover が誤検知してしまうため
@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
trait GatewayDIDesign {

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  val gatewayDesign: Design = newDesign
    .bind[IssuingServiceGateway].to[IssuingServiceGatewayImpl]
    .bind[HouseMoneySettlementNotification].to[HouseMoneySettlementNotificationImpl]
}
