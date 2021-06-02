package jp.co.tis.lerna.payment.adapter.util.metrics

import lerna.management.stats.{ MetricsKey, MetricsValue }

import scala.concurrent.{ ExecutionContext, Future }

/** [[kamon.Kamon]] によって収集されるメトリクスを取得する
  */
trait MetricsReporter {

  /** [[MetricsKey]] に対応する [[MetricsValue]] を取得する */
  def getMetrics(key: MetricsKey)(implicit ec: ExecutionContext): Future[Option[MetricsValue]]

}
