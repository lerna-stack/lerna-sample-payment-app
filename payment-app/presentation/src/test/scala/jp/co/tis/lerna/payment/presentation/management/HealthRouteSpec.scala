package jp.co.tis.lerna.payment.presentation.management

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.adapter.util.health.HealthCheckApplication
import jp.co.tis.lerna.payment.presentation.PresentationDIDesign
import jp.co.tis.lerna.payment.presentation.util.directives.GenTenantDirective
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.{ AppTenant, Example }
import lerna.testkit.airframe.DISessionSupport
import org.scalatest.{ Inside, WordSpec }
import wvlet.airframe.Design

import scala.concurrent.Future

@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
  ),
)
class HealthRouteSpec extends WordSpec with StandardSpec with ScalatestRouteTest with Inside with DISessionSupport {

  override protected val diDesign: Design = PresentationDIDesign.presentationDesign
    .bind[ActorSystem].toInstance(system)
    .bind[Config].toInstance(ConfigFactory.load)
    .bind[HealthCheckApplication].to[HealthCheckApplicationImplMock]
  val route: HealthRoute = diSession.build[HealthRoute]

  "HealthRoute" when {
    "正常系: Ping" in {
      val request = Get("/health")
        .addHeader(RawHeader(GenTenantDirective.headerName, Example.id)) // 機能差異がないので、適当に設定

      request ~> route.route ~> check {
        expect {
          status === StatusCodes.OK
        }
      }
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}

class HealthCheckApplicationImplMock() extends HealthCheckApplication {
  override def healthy()(implicit tenant: AppTenant): Future[Done] = Future.successful(Done)

  /** ヘルスチェックを止めて常に失敗するようにする<br>
    * Graceful Shutdown用
    */
  override def kill(): Unit = {
    // HealthRouteSpec では 使わないので、 ??? のままでOK
    ???
  }
}
