package jp.co.tis.lerna.payment.readmodel

import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import lerna.testkit.airframe.DISessionSupport
import wvlet.airframe.Design

class JDBCSupportSpec extends StandardSpec with DISessionSupport with JDBCSupport {

  @SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
  override protected val diDesign: Design =
    ReadModelDIDesign.readModelDesign
      .bind[Config].toInstance(ConfigFactory.load())

  "JDBCSupport" should {

    import tableSeeds._
    import tables._
    import tables.profile.api._

    "テスト開始前にデータを準備し、結果を検証できる" in withJDBC { db =>
      db.prepare(
        Customer += CustomerRowSeed.copy(walletId = Option("test")),
      )

      db.validate(Customer.result.head) { customer =>
        expect {
          customer.walletId === Option("test")
        }
      }
    }
  }
}
