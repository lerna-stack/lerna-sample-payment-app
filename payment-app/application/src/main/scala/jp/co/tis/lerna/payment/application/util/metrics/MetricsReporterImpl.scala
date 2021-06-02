package jp.co.tis.lerna.payment.application.util.metrics

import jp.co.tis.lerna.payment.adapter.util.metrics.MetricsReporter
import lerna.log.{ AppLogging, LogContext, SystemComponentLogContext }
import lerna.management.stats.{ Metrics, MetricsKey, MetricsValue }

import scala.concurrent.{ ExecutionContext, Future }

/** [[kamon.Kamon]] によって収集されるメトリクスを取得する
  *
  * - [[Metrics]] から取得できる値を必要に応じて変換して返す
  */
final class MetricsReporterImpl(metrics: Metrics, settings: MetricsReporterSettings)
    extends MetricsReporter
    with AppLogging {
  implicit val logContext: LogContext = SystemComponentLogContext.logContext

  private val asLongMetricValueKeys: Set[String] = settings.asLongValueMetricKeys

  override def getMetrics(
      key: MetricsKey,
  )(implicit ec: ExecutionContext): Future[Option[MetricsValue]] = {
    metrics
      .getMetrics(key).map(valueOpt => valueOpt.map(value => convertToLongMetricIfNeeded(key, value)))
  }

  /** [[MetricsKey]] が Long値の文字列を要求する場合には、
    * [[MetricsValue]] の小数点以下を切り捨てて、Long値に変換する
    */
  private def convertToLongMetricIfNeeded(key: MetricsKey, value: MetricsValue) = {
    if (asLongMetricValueKeys.contains(key.key)) {
      val convertedValue = convertToLongMetricValue(value)
      logger.debug(s"MetricsValue is converted: from {} to {}", value.toString, convertedValue.toString)
      convertedValue
    } else {
      value
    }
  }

  /** [[MetricsValue]] に含まれる文字列の小数点以下を切り捨てて、 Long値の文字列に変換する */
  private def convertToLongMetricValue(metricsValue: MetricsValue): MetricsValue = {
    val convertedValue = BigDecimal(metricsValue.value).toLong
    MetricsValue(convertedValue.toString)
  }

}
