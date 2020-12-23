package jp.co.tis.lerna.payment.readmodel

import jp.co.tis.lerna.payment.readmodel
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.utility.tenant.{ AppTenant, Example }
import lerna.testkit.airframe.DISessionSupport
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.concurrent.ScalaFutures.{ timeout, whenReady }
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.{ BeforeAndAfterAll, TestSuite }

@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
trait JDBCSupport extends BeforeAndAfterAll {
  this: TestSuite with DISessionSupport =>

  implicit val tenant: AppTenant = Example

  protected lazy val tableSeeds: TableSeeds = readmodel.TableSeeds(diSession.build[Tables])
  import tableSeeds._

  protected lazy val jdbcService: JDBCService = diSession.build[JDBCService]

  protected val defaultJDBCTimeout: PatienceConfiguration.Timeout = timeout(Span(30, Seconds))

  override def beforeAll(): Unit = {
    import tables.profile.api._
    whenReady(jdbcService.db.run(tables.schema.createIfNotExists), defaultJDBCTimeout) { _ =>
      afterDatabasePrepared()
    }
  }

  protected def afterDatabasePrepared(): Unit = {}

  override def afterAll(): Unit = {
    import tables.profile.api._
    whenReady(jdbcService.db.run(tables.schema.dropIfExists), defaultJDBCTimeout) { _ =>
      // do nothing
    }
  }

  class JDBCHelper {
    import tables.profile.api._
    def prepare(dbActions: DBIO[_]*): Unit = {
      val io = DBIO.seq(dbActions: _*)
      whenReady(jdbcService.db.run(io), defaultJDBCTimeout) { _ => }
    }

    def validate[T](dbAction: DBIO[T])(validator: T => Unit): Unit = {
      whenReady(jdbcService.db.run(dbAction), defaultJDBCTimeout) { result =>
        validator(result)
      }
    }
  }

  def withJDBC(testCode: JDBCHelper => Any): Unit = {
    import tables.profile.api._
    whenReady(jdbcService.db.run(tables.schema.truncate), defaultJDBCTimeout) { _ =>
      testCode(new JDBCHelper)
      whenReady(jdbcService.db.run(tables.schema.truncate), defaultJDBCTimeout) { _ =>
        // do nothing
      }
    }
  }
}
