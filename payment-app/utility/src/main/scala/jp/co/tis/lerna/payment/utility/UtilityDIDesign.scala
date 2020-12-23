package jp.co.tis.lerna.payment.utility

import lerna.util.time.LocalDateTimeFactory
import wvlet.airframe.{ newDesign, Design }

object UtilityDIDesign extends UtilityDIDesign

/** Utility プロジェクト内のコンポーネントの [[wvlet.airframe.Design]] を定義する
  */
// Airframe が生成するコードを Wartremover が誤検知してしまうため
@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
trait UtilityDIDesign {

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  val utilityDesign: Design = newDesign
    .bind[LocalDateTimeFactory].toInstance(LocalDateTimeFactory())

}
