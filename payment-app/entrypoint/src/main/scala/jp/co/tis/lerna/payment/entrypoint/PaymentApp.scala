package jp.co.tis.lerna.payment.entrypoint

import akka.Done
import akka.actor.{ ActorSystem, CoordinatedShutdown, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.util.health.HealthCheckApplication
import jp.co.tis.lerna.payment.application.readmodelupdater.ReadModelUpdaterManager
import jp.co.tis.lerna.payment.presentation.RootRoute
import jp.co.tis.lerna.payment.presentation.util.directives.rejection.AppRejectionHandler
import jp.co.tis.lerna.payment.presentation.util.errorhandling.AppExceptionHandler
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.util.time.JavaDurationConverters._

import scala.concurrent.Future
import scala.util.{ Failure, Success }

@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
class PaymentApp(implicit
    val actorSystem: ActorSystem,
    rootRoute: RootRoute,
    config: Config,
    readModelUpdaterManager: ReadModelUpdaterManager,
    healthCheck: HealthCheckApplication,
) extends AppExceptionHandler
    with AppRejectionHandler {
  import actorSystem.dispatcher

  def start(): Unit = {
    healthCheckWithRetry() onComplete {
      case Success(_) =>
        if (!config.getBoolean("jp.co.tis.lerna.payment.rdbms-read-only")) {
          readModelUpdaterManager.startReadModelUpdaters()
        }
        val privateInternetInterface = config.getString("private-internet.http.interface")
        val privateInternetPort      = config.getInt("private-internet.http.port")

        startServer("private-internet", rootRoute.privateInternetRoute, privateInternetInterface, privateInternetPort)

        val managementInterface = config.getString("management.http.interface")
        val managementPort      = config.getInt("management.http.port")
        startServer("management", rootRoute.managementRoute, managementInterface, managementPort)

      case Failure(_) =>
        System.exit(1)
    }
  }

  private[this] def startServer(typeName: String, route: Route, interface: String, port: Int): Unit = {
    Http()
      .newServerAt(interface, port)
      .bind(Route.seal(route))
      .foreach { serverBinding =>
        addToShutdownHook(typeName, serverBinding)
      }
  }

  private[this] def addToShutdownHook(typeName: String, serverBinding: ServerBinding): Unit = {
    import lerna.log.SystemComponentLogContext.logContext

    val coordinatedShutdown = CoordinatedShutdown(actorSystem)

    coordinatedShutdown.addTask(CoordinatedShutdown.PhaseServiceUnbind, s"http-unbind-$typeName") { () =>
      logger.info(s"[$typeName] 終了処理のため、$serverBinding をunbindします")

      serverBinding.unbind().map(_ => Done)
    }

    coordinatedShutdown.addTask(CoordinatedShutdown.PhaseServiceRequestsDone, s"http-graceful-terminate-$typeName") {
      () =>
        val hardDeadline =
          config.getDuration("jp.co.tis.lerna.payment.entrypoint.graceful-termination.hard-deadline").asScala

        logger.info(s"[$typeName] 終了処理のため、$serverBinding の graceful terminate を開始します（最大で $hardDeadline 待ちます）")

        serverBinding.terminate(hardDeadline) map { _ =>
          logger.info(s"[$typeName] 終了処理のための $serverBinding の graceful terminate が終了しました")

          Done
        }
    }
  }

  private def healthCheckWithRetry(): Future[Done] = {
    // ActorSystemやその拡張機能などの初期化処理で時間がかかる。
    // 初回起動時のHealthCheckは何度か失敗する可能性があるためリトライ処理を行う。
    val healthCheckConfig             = new HealthCheckConfig(config)
    implicit val scheduler: Scheduler = actorSystem.scheduler
    akka.pattern.retry(
      () => tenantsHealthCheck(),
      attempts = healthCheckConfig.retryLimitOnInit,
      minBackoff = healthCheckConfig.retryMinBackoffOnInit,
      maxBackoff = healthCheckConfig.retryMaxBackoffOnInit,
      randomFactor = healthCheckConfig.retryRandomFactorOnInit,
    )
  }

  private[this] def tenantsHealthCheck() = {
    val futures = AppTenant.values.map { implicit tenant =>
      healthCheck.healthy()
    }
    Future.sequence(futures).map(_ => Done)
  }
}
