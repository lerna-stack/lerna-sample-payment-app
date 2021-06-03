package jp.co.tis.lerna.payment.presentation.management

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive1, Route }
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import kamon.Kamon
import lerna.management.stats.{ Metrics, MetricsKey }

import scala.util.{ Success, Try }

class MetricsRoute(config: Config, implicit val system: ActorSystem, metrics: Metrics) extends SprayJsonSupport {

  import system.dispatcher

  Kamon.init(config)

  def route: Route = {
    (pathPrefix("metrics" / Remaining) & extractTenantParameter).tmap(MetricsKey.tupled).apply { key =>
      complete {
        metrics.getMetrics(key).map[(StatusCode, String)] {
          case Some(returnValue) =>
            StatusCodes.OK -> returnValue.value
          case _ =>
            StatusCodes.NotFound -> ""
        }
      }
    }
  }

  private[this] def extractTenantParameter: Directive1[Option[AppTenant]] =
    parameter('tenant.?).flatMap {
      case None => provide(None)
      case Some(tenantId) =>
        Try(AppTenant.withId(tenantId)) match {
          case Success(tenant) => provide(Option(tenant))
          case _               => complete(StatusCodes.BadRequest -> s"""tenant: "$tenantId" is non-existent""")
        }
    }

}
