package jp.co.tis.lerna.payment.entrypoint

import java.security.SecureRandom
import java.security.cert.X509Certificate

import akka.Done
import akka.actor.{ ActorSystem, CoordinatedShutdown, Scheduler }
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.{ ConnectionContext, Http }
import akka.stream.Materializer
import com.typesafe.config.Config
import com.typesafe.sslconfig.ssl.SSLConfigFactory
import javax.net.ssl.{ KeyManager, SSLContext, X509TrustManager }
import jp.co.tis.lerna.payment.adapter.util.health.HealthCheckApplication
import jp.co.tis.lerna.payment.application.readmodelupdater.ReadModelUpdaterSingletonManager
import jp.co.tis.lerna.payment.presentation.RootRoute
import jp.co.tis.lerna.payment.presentation.util.directives.rejection.AppRejectionHandler
import jp.co.tis.lerna.payment.presentation.util.errorhandling.AppExceptionHandler
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import kamon.Kamon
import kamon.system.SystemMetrics
import lerna.management.stats.Metrics
import lerna.util.time.JavaDurationConverters._

import scala.concurrent.Future
import scala.util.{ Failure, Success }

@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
class PaymentApp(implicit
    val actorSystem: ActorSystem,
    rootRoute: RootRoute,
    metrics: Metrics,
    config: Config,
    readModelUpdaterSingletonManager: ReadModelUpdaterSingletonManager,
    healthCheck: HealthCheckApplication,
) extends AppExceptionHandler
    with AppRejectionHandler {
  import actorSystem.dispatcher

  def start(): Unit = {
    healthCheckWithRetry() onComplete {
      case Success(_) =>
        Kamon.addReporter(metrics)
        SystemMetrics.startCollecting()

        if (!config.getBoolean("jp.co.tis.lerna.payment.rdbms-read-only")) {
          readModelUpdaterSingletonManager.startReadModelUpdaters()
        }
        val privateInternetInterface = config.getString("private-internet.http.interface")
        val privateInternetPort      = config.getInt("private-internet.http.port")

        // TODO
        //  AkkaSSLConfig は非推奨になった
        //  - 設定値のパスをAkka非依存になるように変更する (akka.ssl-configは参照しない)
        // See
        //  - https://doc.akka.io/docs/akka/current/project/migration-guide-2.5.x-2.6.x.html#akkasslconfig
        //  - https://github.com/akka/akka/issues/21753
        val disableHostnameVerification: Boolean = {
          val akkaOverrides = config.getConfig("akka.ssl-config")
          val defaults      = config.getConfig("ssl-config")
          val sslConfig     = SSLConfigFactory.parse(akkaOverrides.withFallback(defaults))
          sslConfig.loose.disableHostnameVerification
        }
        if (disableHostnameVerification) {
          enableLooseX509TrustManager()
        }

        startServer("private-internet", rootRoute.privateInternetRoute, privateInternetInterface, privateInternetPort)

        val managementInterface = config.getString("management.http.interface")
        val managementPort      = config.getInt("management.http.port")
        startServer("management", rootRoute.managementRoute, managementInterface, managementPort)

      case Failure(_) =>
        System.exit(1)
    }
  }

  private[this] def startServer(typeName: String, route: Route, interface: String, port: Int)(implicit
      fm: Materializer,
  ): Unit = {
    Http()
      .bindAndHandle(route, interface, port)
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

  /** !!! 開発用に SSL の検証を無効化する設定 !!!
    * https://github.com/akka/akka/issues/18334#issuecomment-137687990
    *
    * FIXME: 本番では使わない
    */
  private[this] def enableLooseX509TrustManager(): Unit = {
    // TODO
    //  - SSLEngineを構築する方法に切り替える
    //  - 可能な限りエントリポイントごとの設定値に変更する (グローバルに変更しない)
    // See
    //  - https://doc.akka.io/docs/akka-http/current/client-side/client-https-support.html#disabling-hostname-verification
    import lerna.log.SystemComponentLogContext.logContext
    logger.warn(
      "Hostname Verification of HTTPS is disabled. This is not intended for the production environment.",
    )
    val trustfulSslContext: SSLContext = {
      object NoCheckX509TrustManager extends X509TrustManager {
        override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = ()
        override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = ()
        override def getAcceptedIssuers: Array[X509Certificate]                                = Array[X509Certificate]()
      }
      val context = SSLContext.getInstance("TLS")
      context.init(Array[KeyManager](), Array(NoCheckX509TrustManager), new SecureRandom())
      context
    }
    Http().setDefaultClientHttpsContext(ConnectionContext.https(trustfulSslContext))
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
