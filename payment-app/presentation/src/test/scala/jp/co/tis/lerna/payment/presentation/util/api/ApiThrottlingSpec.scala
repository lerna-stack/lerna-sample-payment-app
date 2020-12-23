package jp.co.tis.lerna.payment.presentation.util.api

import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.presentation.util.api.impl.ApiThrottlingImpl
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.Example
import lerna.testkit.airframe.DISessionSupport
import lerna.util.tenant.Tenant
import org.scalatest.Inside
import wvlet.airframe.{ newDesign, Design, Session }

@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
  ),
)
class ApiThrottlingSpec extends StandardSpec with Inside with DISessionSupport {

  private def withDiChildSession(childDesign: Design)(testCode: Session => Any) {
    diSession.withChildSession(childDesign) { childSession =>
      testCode(childSession)
    }
  }

  override protected val diDesign: Design = newDesign

  private def generateChildDesign(rootConfig: Config): Design = {
    newDesign
      .bind[ApiThrottling].to[ApiThrottlingImpl]
      .bind[Config].toInstance(rootConfig)
  }

  private val defaultConfig = ConfigFactory.load()

  private implicit val tenant: Tenant = Example

  private val apiId = ApiId.BASE

  "stateOf(apiId)" when {
    "APIは閉局している(active == off)" should {
      val rootConfig = ConfigFactory
        .parseString(s"""
           |jp.co.tis.lerna.payment.presentation.util.api.tenants.${tenant.id} {
           |  ${apiId} {
           |    // on: 開局, off: 閉局
           |    active = off
           |  }
           |}
           |""".stripMargin).withFallback(defaultConfig)

      "Inactive" in {
        withDiChildSession(generateChildDesign(rootConfig)) { session =>
          val apiThrottlingState = session.build[ApiThrottling].stateOf(apiId)
          expect(apiThrottlingState === Inactive)
        }
      }
    }

    "APIは開局している(active == on)" when {
      "流量制限はOFF(rate-limit.active == off)" should {
        val rootConfig = ConfigFactory
          .parseString(s"""
                                                      |jp.co.tis.lerna.payment.presentation.util.api.tenants.${tenant.id} {
                                                      |  ${apiId} {
                                                      |    // on: 開局, off: 閉局
                                                      |    active = on
                                                      |    rate-limit {
                                                      |      // 流量制限 (TPS)
                                                      |      active = off
                                                      |    }
                                                      |  }
                                                      |}
                                                      |""".stripMargin).withFallback(defaultConfig)

        "Nolimit" in {
          withDiChildSession(generateChildDesign(rootConfig)) { session =>
            val apiThrottlingState = session.build[ApiThrottling].stateOf(apiId)
            expect(apiThrottlingState === Nolimit)
          }
        }
      }

      "流量制限はON(rate-limit.active == on, 2.0 TPS)" when {
        import scala.concurrent.duration._
        val transactions = 2
        val duration     = 1.second

        val rootConfig = ConfigFactory
          .parseString(s"""
                                                      |jp.co.tis.lerna.payment.presentation.util.api.tenants.${tenant.id} {
                                                      |  ${apiId} {
                                                      |    // on: 開局, off: 閉局
                                                      |    active = on
                                                      |    rate-limit {
                                                      |      // 流量制限 (TPS)
                                                      |      active = on
                                                      |      transactions = ${transactions}
                                                      |      duration     = ${duration.toSeconds} s // 秒以上単位
                                                      |    }
                                                      |  }
                                                      |}
                                                      |""".stripMargin).withFallback(defaultConfig)

        "初期化直後" should {
          "リクエストは1回だけ許可される" in {
            withDiChildSession(generateChildDesign(rootConfig)) { session =>
              val apiThrottlingState = session.build[ApiThrottling].stateOf(apiId)
              inside(apiThrottlingState) {
                case limited: Limited =>
                  expect(limited.tryAcquire() === true)
                  expect(limited.tryAcquire() === false)
                  expect(limited.tryAcquire() === false)
              }
            }
          }
        }

        "TPSの逆数時間経過後" should {
          "2回目のリクエストが許可される" in {
            withDiChildSession(generateChildDesign(rootConfig)) { session =>
              val apiThrottlingState = session.build[ApiThrottling].stateOf(apiId)
              inside(apiThrottlingState) {
                case limited: Limited =>
                  expect(limited.tryAcquire() === true)
                  expect(limited.tryAcquire() === false)
                  expect(limited.tryAcquire() === false)
                  Thread.sleep(duration.toMillis / transactions)
                  expect(limited.tryAcquire() === true)
                  expect(limited.tryAcquire() === false)
                  expect(limited.tryAcquire() === false)
              }
            }
          }
        }

        "1秒間リクエストがない場合" should {
          "TPS * 1sのリクエストまで保存できる" in {
            withDiChildSession(generateChildDesign(rootConfig)) { session =>
              val apiThrottlingState = session.build[ApiThrottling].stateOf(apiId)
              Thread.sleep(1000) // burst 機能（保存）のため待つ
              inside(apiThrottlingState) {
                case limited: Limited =>
                  (1 to transactions).foreach { _ =>
                    expect(limited.tryAcquire() === true) // 保存分
                  }
                  expect(limited.tryAcquire() === true) // ↑で消費してすぐに使用できるようになった分
                  expect(limited.tryAcquire() === false)
                  expect(limited.tryAcquire() === false)
              }
            }
          }
        }

        "2秒間リクエストがない場合" should {
          "TPS * 1sのリクエストまで保存できる" in {
            withDiChildSession(generateChildDesign(rootConfig)) { session =>
              val apiThrottlingState = session.build[ApiThrottling].stateOf(apiId)
              Thread.sleep(2000) // burst 機能（保存）のため待つ
              inside(apiThrottlingState) {
                case limited: Limited =>
                  (1 to transactions).foreach { _ =>
                    expect(limited.tryAcquire() === true) // 保存分
                  }
                  expect(limited.tryAcquire() === true) // ↑で消費してすぐに使用できるようになった分
                  expect(limited.tryAcquire() === false)
                  expect(limited.tryAcquire() === false)
              }
            }
          }
        }
      }
    }
  }
}
