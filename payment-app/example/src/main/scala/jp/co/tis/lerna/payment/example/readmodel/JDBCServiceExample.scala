package jp.co.tis.lerna.payment.example.readmodel

import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.readmodel.{ JDBCService, ReadModelDIDesign }

object JDBCServiceExample {

  // ① JDBCService と Tables をコンストラクタインジェクションで取得
  class MyComponent(jdbcService: JDBCService, tables: Tables) {
    // ② api をインポート
    import tables.profile.api._

    def run(): Unit = {
      // ③ クエリ作成
      val dbio: DBIO[Seq[tables.CustomerRow]] = tables.Customer.result
      // ④ クエリ実行
      jdbcService.db.run(dbio)
    }
  }

  // Airframe が生成するコードを Wartremover が誤検知してしまうため
  @SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
  def main(args: Array[String]): Unit = {
    ReadModelDIDesign.readModelDesign
      .bind[Config].toInstance(ConfigFactory.load())
      .bind[MyComponent].toSingleton.withSession { session =>
        session.build[MyComponent].run()
      }
  }
}
