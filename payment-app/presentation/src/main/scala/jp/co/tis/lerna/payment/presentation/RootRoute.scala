package jp.co.tis.lerna.payment.presentation

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import jp.co.tis.lerna.payment.presentation.ecpayment.issuing.IssuingService
import jp.co.tis.lerna.payment.presentation.management.{ HealthRoute, MetricsRoute, Shutdown, VersionRoute }
import jp.co.tis.lerna.payment.presentation.util.directives.{
  EditorContentTypeDirective,
  GenAppRequestContextDirective,
}
import lerna.http.directives.RequestLogDirective
import lerna.log.AppLogging

class RootRoute(
    issuingService: IssuingService,
    metricsRoute: MetricsRoute,
    healthRoute: HealthRoute,
    versionRoute: VersionRoute,
    shutdown: Shutdown,
) extends GenAppRequestContextDirective
    with EditorContentTypeDirective
    with AppLogging
    with RequestLogDirective {

  def privateInternetRoute: Route =
    extractAppRequestContext { implicit appRequestContext =>
      logRequestDirective.apply {
        logRequestResultDirective.apply {
          respondWithContentTypeApplicationJsonUTF8 {
            concat(
              issuingService.route(),
            )
          }
        }
      }
    }

  /** システム内部からしか呼ばれない管理用のエンドポイントを定義する Route
    */
  def managementRoute: Route = concat(
    metricsRoute.route,
    healthRoute.route,
    versionRoute.route,
    shutdown.route,
  )
}
