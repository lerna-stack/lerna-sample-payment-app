package jp.co.tis.lerna.payment.application.util.metrics

import com.typesafe.config.ConfigFactory
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec

import scala.util.Try

@SuppressWarnings(
  Array(
    "org.wartremover.warts.TryPartial",
    "org.wartremover.warts.IsInstanceOf",
  ),
)
final class MetricsReporterSettingsSpec extends StandardSpec {

  s"適切な設定の場合には  ${classOf[MetricsReporterSettings].getSimpleName} を作成できること" in {
    val config = ConfigFactory.parseString("""
        |jp.co.tis.lerna.payment.application.util.metrics.as-long-value-metrics-keys = [
        |  "simple-key",
        |  "/some-path/key-A",
        |  "/some-path/key-B",
        |]
        |lerna.management.stats.metrics-reporter = {
        |  "simple-key" {
        |    name = "simple-key"
        |  }
        |  "/some-path/key-A" {
        |    name = "key-A"
        |  }
        |  "/some-path/key-B" {
        |    name = "key-B"
        |  }
        |}
        |""".stripMargin)

    val settings = MetricsReporterSettings.fromConfig(config)
    assert(settings.config === config)
    assert(settings.asLongValueMetricKeys.size === 3)
    assert(settings.asLongValueMetricKeys.contains("simple-key"))
    assert(settings.asLongValueMetricKeys.contains("some-path/key-A"))
    assert(settings.asLongValueMetricKeys.contains("some-path/key-B"))
  }

  s"metrics-reporter に定義されていない名前が as-long-value-metrics-keys に含まれる場合は ${classOf[MetricsReporterSettings].getSimpleName} を作成できないこと" in {
    val config = ConfigFactory.parseString("""
        |jp.co.tis.lerna.payment.application.util.metrics.as-long-value-metrics-keys = [
        |  "/some-path/invalid-key-A",
        |  "/some-path/valid-key-B",
        |]
        |lerna.management.stats.metrics-reporter = {
        |  "/some-path/valid-key-B" {
        |    name = "valid-key-B"
        |  }
        |}
        |""".stripMargin)

    val cause = Try(MetricsReporterSettings.fromConfig(config)).failed.get
    assert(cause.isInstanceOf[IllegalArgumentException])
    assert(cause.getMessage === "some-path/invalid-key-A does not declared at lerna.management.stats.metrics-reporter")
  }

  s"設定ファイルから ${classOf[MetricsReporterSettings].getSimpleName} が作成できること" in {
    val config            = ConfigFactory.load
    val settingsOrFailure = MetricsReporterSettings.tryFromConfig(config)
    assert(settingsOrFailure.isSuccess)
  }

}
