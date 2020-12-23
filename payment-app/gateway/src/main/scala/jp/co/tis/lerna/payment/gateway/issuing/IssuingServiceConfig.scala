package jp.co.tis.lerna.payment.gateway.issuing

import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.util.time.JavaDurationConverters._

import scala.concurrent.duration.FiniteDuration

/**  IssuingService 設定ファイル
  *
  * @param rootConfig 全体設定ファイル
  */
class IssuingServiceConfig(rootConfig: Config) {
  private val config = rootConfig.getConfig("jp.co.tis.lerna.payment.gateway.issuing")

  private def baseUrl(implicit tenant: AppTenant): String = config.getString(s"tenants.${tenant.id}.base-url")
  private def path(implicit tenant: AppTenant): String    = config.getString(s"tenants.${tenant.id}.path")

  def url(implicit tenant: AppTenant): Uri = Uri(s"$baseUrl$path")

  def responseTimeout(implicit tenant: AppTenant): FiniteDuration =
    config.getDuration(s"tenants.${tenant.id}.response-timeout").asScala

  def retryAttempts(implicit tenant: AppTenant): Int =
    config.getInt(s"tenants.${tenant.id}.retry.attempts")
  def retryDelay(implicit tenant: AppTenant): FiniteDuration =
    config.getDuration(s"tenants.${tenant.id}.retry.delay").asScala
}
