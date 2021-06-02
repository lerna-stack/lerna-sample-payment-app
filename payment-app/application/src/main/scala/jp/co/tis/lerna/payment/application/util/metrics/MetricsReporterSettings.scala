package jp.co.tis.lerna.payment.application.util.metrics

import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._
import scala.util.Try

/** [[MetricsReporterImpl]] の設定
  *
  * @param config 使用している [[Config]]
  * @param asLongValueMetricKeys [[Long]] 値を要求する [[lerna.management.stats.MetricsKey]]s 一覧
  */
final class MetricsReporterSettings private (val config: Config, val asLongValueMetricKeys: Set[String])

object MetricsReporterSettings {
  private val logger = LoggerFactory.getLogger(getClass)

  private val asLongValueMetricKeysConfigPath: String =
    "jp.co.tis.lerna.payment.application.util.metrics.as-long-value-metrics-keys"
  private val metricReportersConfigPath: String =
    "lerna.management.stats.metrics-reporter"

  /** [[Config]] から [[MetricsReporterSettings]] を作成する
    *
    * 作成に失敗した場合は 原因を含む [[scala.util.Failure]] を返す
    */
  def tryFromConfig(config: Config): Try[MetricsReporterSettings] = {
    Try(fromConfig(config))
  }

  /** [[Config]] から [[MetricsReporterSettings]] を作成する
    *
    * @throws IllegalArgumentException [[Config]] に不正な値が含まれている
    */
  def fromConfig(config: Config): MetricsReporterSettings = {

    val asLongMetricsValueKeys: Set[String] = parseAsLongMetricValueKeys(config)
    logger.debug("AsLongValueMetricKeys: {}", asLongMetricsValueKeys.toString)

    val validMetricsKeys = parseValidMetricsKeysFromConfig(config)
    logger.debug("ValidMetricsKeyKeys: {}", validMetricsKeys.toString)

    // Validate whether all of the asLongMetricsValueKeys are valid or not
    asLongMetricsValueKeys.foreach { key =>
      if (!validMetricsKeys.contains(key)) {
        throw new IllegalArgumentException(s"${key} does not declared at ${metricReportersConfigPath}")
      }
    }

    new MetricsReporterSettings(config, asLongMetricsValueKeys)
  }

  /** [[Config]] から Long値として扱う [[lerna.management.stats.MetricsKey]] 一覧を取得する */
  private def parseAsLongMetricValueKeys(config: Config): Set[String] = {
    config
      .getStringList(asLongValueMetricKeysConfigPath)
      .asScala
      .map(convertMetricKey)
      .toSet
  }

  /** [[Config]] から  有効な [[lerna.management.stats.MetricsKey]] 一覧を取得する */
  private def parseValidMetricsKeysFromConfig(config: Config): Set[String] = {
    config
      .getConfig(metricReportersConfigPath)
      .root()
      .entrySet()
      .asScala
      .map(entry => entry.getKey)
      .map(convertMetricKey)
      .toSet
  }

  /** [[lerna.management.stats.MetricsKey]] で使用する `key` を [[lerna.management.stats.Metrics]] が受け付ける形式に変換する */
  private def convertMetricKey(key: String): String = {
    key.replaceFirst("^/", "")
  }

}
