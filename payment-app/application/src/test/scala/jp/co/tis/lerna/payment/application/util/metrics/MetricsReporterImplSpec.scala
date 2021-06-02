package jp.co.tis.lerna.payment.application.util.metrics

import akka.actor.{ ActorSystem, Scheduler }
import akka.pattern.retry
import akka.testkit.TestKit
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.application.ApplicationDIDesign
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import kamon.Kamon
import lerna.management.stats.{ Metrics, MetricsKey }
import lerna.testkit.airframe.DISessionSupport
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }
import wvlet.airframe.Design

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object MetricsReporterImplSpec {
  val config: Config = {
    val overrideConfig = ConfigFactory.parseString("""
        |kamon.metric.tick-interval = 10 seconds
        |""".stripMargin)
    overrideConfig.withFallback(ConfigFactory.load)
  }
}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.OptionPartial",
    "org.wartremover.contrib.warts.MissingOverride",
  ),
)
final class MetricsReporterImplSpec
    extends TestKit(ActorSystem("HealthCheckApplicationSpec", MetricsReporterImplSpec.config))
    with StandardSpec
    with BeforeAndAfterAll
    with ScalaFutures
    with DISessionSupport {

  override protected val diDesign: Design =
    ApplicationDIDesign.applicationDesign
      .bind[ActorSystem].toInstance(system)
      .bind[Config].toInstance(system.settings.config)

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Metrics は Kamon.init の前にインスタンス化する必要があるため
    diSession.build[Metrics]
    Kamon.init(system.settings.config)
  }

  override def afterAll(): Unit = {
    try shutdown()
    finally super.afterAll()
  }

  val metricsReporter: MetricsReporterImpl        = diSession.build[MetricsReporterImpl]
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val scheduler: Scheduler               = system.scheduler

  // kamon.metric.tick-interval = 10 seconds なので、
  // その間隔よりも十分に長い時間をタイムアウトに設定する。
  val timeout: Timeout = Timeout(scaled(Span(30, Seconds)))
  val attempts: Int    = 30
  val delay: Span      = scaled(1000.millis)

  "should collect a jvm_heap_used value as Long" in {
    val key = MetricsKey("system-metrics/jvm-memory/heap/used", None)
    whenReady(
      retry(() => metricsReporter.getMetrics(key).map(_.get), attempts, delay),
      timeout,
    ) { metrics =>
      expect(metrics.value.toLong > 0)
    }
  }

  "should collect a jvm_heap_max value as Long" in {
    val key = MetricsKey("system-metrics/jvm-memory/heap/max", None)
    whenReady(
      retry(() => metricsReporter.getMetrics(key).map(_.get), attempts, delay),
      timeout,
    ) { metrics =>
      expect(metrics.value.toLong > 0)
    }
  }

}
