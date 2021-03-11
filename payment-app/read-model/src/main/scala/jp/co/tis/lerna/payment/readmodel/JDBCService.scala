package jp.co.tis.lerna.payment.readmodel

import com.typesafe.config.Config
import jp.co.tis.lerna.payment.utility.tenant.{ AppTenant, Example }
import lerna.util.lang.Equals._
import slick.basic.DatabaseConfig
import slick.jdbc.{ JdbcBackend, JdbcProfile }
import wvlet.airframe._

// Airframe が生成するコードを Wartremover が誤検知してしまうため
@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
trait JDBCService {

  private[this] val config = bind[Config]

  def db()(implicit tenant: AppTenant): JdbcBackend#Database = {
    dbConfigMap(tenant).db
  }

  def dbConfig()(implicit tenant: AppTenant): DatabaseConfig[JdbcProfile] = {
    dbConfigMap(tenant)
  }

  private[this] val dbConfigMap = AppTenant.values.map { tenant =>
    tenant -> generateDbConfig(tenant)
  }.toMap

  private[this] def generateDbConfig(tenant: AppTenant): DatabaseConfig[JdbcProfile] = {
    DatabaseConfig.forConfig(s"jp.co.tis.lerna.payment.readmodel.rdbms.tenants.${tenant.id}", config)
  }

  /** trait Tables の override val profile: JdbcProfile 用
    *
    * ※ profile の指定は 末尾に `$` を付与して object 指定である必要あり
    * @return conf の profile から 取得した profile object (singleton)
    */
  private[readmodel] def profile: JdbcProfile = {
    dbConfigMap(Example).profile
      .ensuring(
        returnValue => dbConfigMap.values.forall(_.profile === returnValue),
        "全てのテナントで rdbms の profile は同じものを使う必要があります",
      )
  }

  private[this] def connectAndAddShutdownHookToAllDataBase(): Unit = {
    dbConfigMap.values.foreach { dbConfig =>
      // dbConfig.db は `lazy val` で定義されているためアクセスしないと DB に接続されない
      // 起動時にチェックして、リクエスト時に初めてエラーが判明することを回避する
      dbConfig.db.onShutdown { db =>
        db.close()
      }
    }
  }

  connectAndAddShutdownHookToAllDataBase()
}
