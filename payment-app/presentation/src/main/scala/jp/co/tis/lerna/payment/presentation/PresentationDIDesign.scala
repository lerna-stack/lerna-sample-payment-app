package jp.co.tis.lerna.payment.presentation

import jp.co.tis.lerna.payment.presentation.ecpayment.issuing.IssuingService
import jp.co.tis.lerna.payment.presentation.util.api.ApiThrottling
import jp.co.tis.lerna.payment.presentation.util.api.impl.ApiThrottlingImpl
import jp.co.tis.lerna.payment.presentation.util.directives.{
  AuthorizationHeaderDirective,
  AuthorizationHeaderDirectiveImpl,
}
import wvlet.airframe.{ newDesign, Design }

object PresentationDIDesign extends PresentationDIDesign

/** Presentation プロジェクト内のコンポーネントの [[wvlet.airframe.Design]] を定義する
  */
// Airframe が生成するコードを Wartremover が誤検知してしまうため
@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
trait PresentationDIDesign {

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  val presentationDesign: Design = newDesign
    .bind[RootRoute].toSingleton
    .bind[ApiThrottling].to[ApiThrottlingImpl]
    .bind[AuthorizationHeaderDirective].to[AuthorizationHeaderDirectiveImpl]
    .bind[IssuingService].toSingleton
}
