/**
  * Slick からコピーして一部を書き換えたコードです。
  * 詳しい経緯は README を参照してください。
  */
import slick.dbio.DBIO
import slick.jdbc.meta.MTable
import slick.jdbc.{ JdbcModelBuilder, MySQLProfile }
import slick.model.Model

import scala.concurrent.ExecutionContext

class CustomJdbcModelComponentExt(component: MySQLProfile) {

  def createModel(tables: Option[DBIO[Seq[MTable]]] = None, ignoreInvalidDefaults: Boolean = true)(
      implicit ec: ExecutionContext,
  ): DBIO[Model] = {
    val tablesA = tables.getOrElse(component.defaultTables)
    tablesA.flatMap(t => createModelBuilder(t, ignoreInvalidDefaults).buildModel)
  }

  def createModelBuilder(tables: Seq[MTable], ignoreInvalidDefaults: Boolean)(
      implicit ec: ExecutionContext,
  ): JdbcModelBuilder = {
    new CustomMySQLModelBuilder(tables, ignoreInvalidDefaults)
  }
}
