package jp.co.tis.lerna.payment.readmodel

import jp.co.tis.lerna.payment.readmodel.schema.Tables
import slick.jdbc.JdbcProfile
import wvlet.airframe._

object ReadModelDIDesign extends ReadModelDIDesign

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
trait ReadModelDIDesign {

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  val readModelDesign: Design = newDesign
    .bind[JDBCService].toSingleton
    .bind[Tables].toSingletonProvider { jdbc: JDBCService =>
      new Tables {
        override val profile: JdbcProfile = jdbc.profile
      }
    }
}
