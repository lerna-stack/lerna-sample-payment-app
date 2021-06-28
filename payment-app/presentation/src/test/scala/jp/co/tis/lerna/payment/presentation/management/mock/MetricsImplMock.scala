package jp.co.tis.lerna.payment.presentation.management.mock

import com.typesafe.config.Config
import kamon.Kamon
import kamon.metric.PeriodSnapshot
import lerna.management.stats.{ Metrics, MetricsKey, MetricsValue }

import scala.concurrent.Future

final case class Settings(
    sendItems: Seq[_ <: Config],
)

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
  ),
)
class MetricsImplMock(config: Config = Kamon.config()) extends Metrics {

  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = ???

  override def getMetrics(key: MetricsKey): Future[Option[MetricsValue]] = {
    val returnValue = key match {
      case MetricsKey("jvm_heap_used", _) => Option(MetricsValue("111111"))
      case MetricsKey("jvm_heap_max", _)  => Option(MetricsValue("222222"))
      case _                              => None
    }

    Future.successful(returnValue)

  }

  override def reconfigure(config: Config): Unit = ???

  override def stop(): Unit = {}

}
