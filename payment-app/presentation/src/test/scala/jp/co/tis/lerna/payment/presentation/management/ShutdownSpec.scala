package jp.co.tis.lerna.payment.presentation.management

import akka.Done
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.util.health.HealthCheckApplication
import jp.co.tis.lerna.payment.adapter.util.shutdown.GracefulShutdownApplication
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.testkit.airframe.DISessionSupport
import org.scalatest.{ Inside, WordSpec }
import wvlet.airframe.{ newDesign, Design }

import scala.concurrent.Future

object ShutdownSpec {
  private class HealthCheckApplicationMock extends HealthCheckApplication {
    override def healthy()(implicit tenant: AppTenant): Future[Done] = {
      // ShutdownSpec では 使わないので、 ??? のままでOK
      ???
    }

    /** ヘルスチェックを止めて常に失敗するようにする<br>
      * Graceful Shutdown用
      */
    override def kill(): Unit = {
      // do nothing
    }
  }

  private class GracefulShutdownApplicationMock extends GracefulShutdownApplication {

    /** ShardRegionを GracefulShutdown する<br>
      * ※ 非同期に shutdown されるので、呼び出しが完了した時点で shutdown完了ではない
      */
    override def requestGracefulShutdownShardRegion(): Unit = {
      // do nothing
    }

    /** ReadModelUpdaterSupervisorを Shutdown の準備させる<br>
      * ※ 非同期に shutdown の準備されるので、呼び出しが完了した時点で shutdownの準備完了ではない
      */
    override def requestShutdownReadyReadModelUpdaterSupervisor(): Future[Any] = {
      // do nothing
      Future.successful(Done)
    }
  }
}

// Lint回避のため
@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
  ),
)
class ShutdownSpec extends WordSpec with StandardSpec with ScalatestRouteTest with Inside with DISessionSupport {

  import ShutdownSpec._

  override protected val diDesign: Design = newDesign
    .bind[Config].toInstance(system.settings.config)
    .bind[HealthCheckApplication].to[HealthCheckApplicationMock]
    .bind[GracefulShutdownApplication].to[GracefulShutdownApplicationMock]

  private val route = diSession.build[Shutdown].route

  "/shutdown/hand-off-service" should {
    "Postリクエストを受け付ける" in {
      Post("/shutdown/hand-off-service") ~> route ~> check {
        expect {
          status === StatusCodes.Accepted
          responseAs[String] === "新規処理の受付停止リクエストを受け付けました"
        }
      }
    }

    "Getリクエストを受け付けない" in {
      Get("/shutdown/hand-off-service") ~> route ~> check {
        expect {
          rejections.nonEmpty
        }
      }
    }
  }
}
