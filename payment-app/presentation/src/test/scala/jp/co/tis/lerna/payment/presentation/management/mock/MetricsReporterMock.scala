package jp.co.tis.lerna.payment.presentation.management.mock

import jp.co.tis.lerna.payment.adapter.util.metrics.MetricsReporter
import lerna.management.stats.{ MetricsKey, MetricsValue }

import scala.concurrent.{ ExecutionContext, Future }

class MetricsReporterMock() extends MetricsReporter {

  override def getMetrics(key: MetricsKey)(implicit ec: ExecutionContext): Future[Option[MetricsValue]] = {
    val returnValue = key match {
      case MetricsKey("jvm_heap_used", _) => Option(MetricsValue("111111"))
      case MetricsKey("jvm_heap_max", _)  => Option(MetricsValue("222222"))
      case _                              => None
    }
    Future.successful(returnValue)
  }

}
