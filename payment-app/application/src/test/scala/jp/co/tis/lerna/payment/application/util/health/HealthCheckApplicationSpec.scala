package jp.co.tis.lerna.payment.application.util.health

import akka.Done
import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.utility.UtilityDIDesign
import lerna.util.time.{ FixedLocalDateTimeFactory, LocalDateTimeFactory }
import jp.co.tis.lerna.payment.adapter.util.health.HealthCheckApplication
import jp.co.tis.lerna.payment.readmodel.ReadModelDIDesign
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.{ AppTenant, Example }
import lerna.testkit.airframe.DISessionSupport
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Millis, Seconds, Span }
import wvlet.airframe.Design

import scala.concurrent.Future

// Lint回避のため
@SuppressWarnings(
  Array(
    "lerna.warts.Awaits",
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
class HealthCheckApplicationSpec
    extends TestKit(ActorSystem("HealthCheckApplicationSpec"))
    with StandardSpec
    with BeforeAndAfterAll
    with ScalaFutures
    with DISessionSupport {

  override val diDesign: Design = UtilityDIDesign.utilityDesign
    .bind[LocalDateTimeFactory].toInstance(FixedLocalDateTimeFactory("2019-05-01T00:00:00Z"))
    .add(ReadModelDIDesign.readModelDesign)
    .bind[Config].toInstance(ConfigFactory.load)
    .bind[ActorSystem].toInstance(system)
    .bind[HealthCheckApplication].to[HealthCheckApplicationImpl]

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  private implicit val tenant: AppTenant = Example

  "HealthCheckApplication#healthy()" when {
    val healthCheckApplication = diSession.build[HealthCheckApplication]

    "正常系" should {
      "Future.successful(Done)を返す" in {
        val future: Future[Done] = healthCheckApplication.healthy()
        whenReady(future) { result =>
          expect {
            result === Done
          }
        }
      }
    }

    "killしたら" should {
      "常に unhealthy(Future.failed) を返す" in {
        // 一度kill すると他のテストで使い回せないため別sessionにする
        val newSession = diDesign.newSession

        val healthCheckApplication = newSession.build[HealthCheckApplication]

        healthCheckApplication.kill()

        val future: Future[Done] = healthCheckApplication.healthy()
        whenReady(future.failed) { throwable =>
          expect(throwable.getMessage === "ヘルスチェック機能は停止されています")
        }

        newSession.close()
      }
    }
  }

  override def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }
}
