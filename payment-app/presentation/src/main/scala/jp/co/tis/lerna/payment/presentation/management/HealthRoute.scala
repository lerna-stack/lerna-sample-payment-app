package jp.co.tis.lerna.payment.presentation.management

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import jp.co.tis.lerna.payment.adapter.util.health.HealthCheckApplication
import jp.co.tis.lerna.payment.presentation.util.directives.GenAppRequestContextDirective

import scala.util.{ Failure, Success }

/** ヘルスチェック用のエンドポイント
  * HAProxy と Keepalived から、アプリのプロセスが生きているかチェックするためのエンドポイント
  */
class HealthRoute(healthCheck: HealthCheckApplication) extends GenAppRequestContextDirective {
  def route: Route = pathPrefix("health") {
    extractAppRequestContext { implicit appRequestContext =>
      onComplete(healthCheck.healthy()) {
        case Success(_) =>
          complete(StatusCodes.OK -> "OK")
        case Failure(_) =>
          complete(StatusCodes.ServiceUnavailable -> "NG")
      }
    }
  }
}
