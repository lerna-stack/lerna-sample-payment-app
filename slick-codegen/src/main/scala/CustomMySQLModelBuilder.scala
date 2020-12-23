/**
  * Slick からコピーして一部を書き換えたコードです。
  * 詳しい経緯は README を参照してください。
  */
import slick.jdbc.MySQLProfile
import slick.jdbc.meta._

import scala.concurrent.ExecutionContext

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Product",
    "org.wartremover.warts.Serializable",
    "org.wartremover.contrib.warts.SomeApply",
  ),
)
class CustomMySQLModelBuilder(mTables: Seq[MTable], ignoreInvalidDefaults: Boolean)(implicit ec: ExecutionContext)
    extends MySQLProfile.ModelBuilder(mTables, ignoreInvalidDefaults)(ec) {
  override def createColumnBuilder(tableBuilder: TableBuilder, meta: MColumn): ColumnBuilder =
    new CustomColumnBuilder(tableBuilder, meta)

  @SuppressWarnings(
    Array(
      "org.wartremover.contrib.warts.SomeApply",
      "org.wartremover.warts.Equals",
      "lerna.warts.CyclomaticComplexity",
    ),
  )
  class CustomColumnBuilder(tableBuilder: TableBuilder, meta: MColumn) extends ColumnBuilder(tableBuilder, meta) {
    override def default =
      meta.columnDef
        .map((_, tpe)).collect {
          case ("NULL", _)               => None
          case (v, "String")             => Some(Some(v))
          case ("1" | "b'1'", "Boolean") => Some(Some(true))
          case ("0" | "b'0'", "Boolean") => Some(Some(false))
          case (v, "scala.math.BigDecimal") => {
            Some(Some(scala.math.BigDecimal(v)))
          }
        }.getOrElse {
          val d = super.default
          if (meta.nullable == Some(true) && d == None) {
            Some(None)
          } else d
        }
  }
}
