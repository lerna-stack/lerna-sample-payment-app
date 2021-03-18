package jp.co.tis.lerna.payment.presentation.management

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.util.health.HealthCheckApplication
import jp.co.tis.lerna.payment.adapter.util.shutdown.GracefulShutdownApplication
import lerna.log.AppLogging

class Shutdown(
    config: Config,
    healthCheckApplication: HealthCheckApplication,
    gracefulShutdownApplication: GracefulShutdownApplication,
) extends AppLogging {

  def route: Route = {
    pathPrefix("shutdown") {
      path("hand-off-service") {
        // うっかり GET アクセスして止まることを回避するために POST のみ受けつける
        post {
          import lerna.log.SystemComponentLogContext.logContext
          logger.info("新規処理の受付停止リクエストを受け付けました")

          healthCheckApplication.kill()
          gracefulShutdownApplication.requestGracefulShutdownShardRegion()

          complete(StatusCodes.Accepted -> "新規処理の受付停止リクエストを受け付けました")
        }
      }
    }
  }
}
