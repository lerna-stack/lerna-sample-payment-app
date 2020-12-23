package jp.co.tis.lerna.payment.entrypoint

import lerna.util.time.JavaDurationConverters._
import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

// ヘルスチェックの設定
// 初回起動時のリトライ処理を扱っている。
final class HealthCheckConfig(root: Config) {
  val retryLimitOnInit: Int = {
    root.getInt("management.http.healthcheck.retry-limit-on-init")
  }
  val retryMinBackoffOnInit: FiniteDuration = {
    root.getDuration("management.http.healthcheck.retry-min-backoff-on-init").asScala
  }
  val retryMaxBackoffOnInit: FiniteDuration = {
    root.getDuration("management.http.healthcheck.retry-max-backoff-on-init").asScala
  }
  val retryRandomFactorOnInit: Double = {
    root.getDouble("management.http.healthcheck.retry-random-factor-on-init")
  }
}
