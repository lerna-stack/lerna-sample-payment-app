package jp.co.tis.lerna.payment.presentation.util.api.impl

import com.google.common.util.concurrent.RateLimiter
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.presentation.util.api.ApiId.ApiId
import jp.co.tis.lerna.payment.presentation.util.api._
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.log.AppLogging
import lerna.util.tenant.Tenant

class ApiThrottlingImpl(rootConfig: Config) extends ApiThrottling with AppLogging {
  import ApiThrottlingImpl._

  private val config = rootConfig.getConfig("jp.co.tis.lerna.payment.presentation.util.api")

  // 初期化
  private[this] val apiThrottlingStateMap: Map[(ApiId, Tenant), ApiThrottlingState] = {
    (for {
      tenant <- AppTenant.values
      apiId  <- ApiId.values.toSeq
    } yield {
      val apiConfig = config.getConfig(s"tenants.${tenant.id}.${apiId.toString}")
      val apiThrottlingState: ApiThrottlingState =
        if (!apiConfig.getBoolean("active")) Inactive
        else if (!apiConfig.getBoolean("rate-limit.active")) Nolimit
        else {
          val transactions     = apiConfig.getDouble("rate-limit.transactions")
          val duration         = apiConfig.getDuration("rate-limit.duration")
          val permitsPerSecond = transactions / duration.getSeconds
          val rateLimiter      = RateLimiter.create(permitsPerSecond)
          LimitedImpl(rateLimiter)
        }

      (apiId, tenant) -> apiThrottlingState
    }).toMap
  }

  override def stateOf(apiId: ApiId)(implicit tenant: Tenant): ApiThrottlingState =
    apiThrottlingStateMap.get((apiId, tenant)) match {
      case Some(apiThrottlingState) => apiThrottlingState
      case None =>
        throw new IllegalStateException(s"流量制限設定不備のため処理できません。 apiId: ${apiId.toString}, tenant: ${tenant.id}")
    }
}

object ApiThrottlingImpl {
  final case class LimitedImpl(rateLimiter: RateLimiter) extends Limited {
    override def tryAcquire(): Boolean = rateLimiter.tryAcquire()
  }
}
