package jp.co.tis.lerna.payment.gateway.mock

import wvlet.airframe.{ newDesign, Design }

// Airframe が生成するコードを Wartremover が誤検知してしまうため
@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
object ExternalServiceMockDIDesign {

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  val externalServiceMockDesign: Design = newDesign
    .bind[IssuingServiceMock].toSingleton
    .bind[NotificationSystemMock].toSingleton
}
