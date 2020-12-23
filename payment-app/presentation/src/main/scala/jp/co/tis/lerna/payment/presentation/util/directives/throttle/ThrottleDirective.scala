package jp.co.tis.lerna.payment.presentation.util.directives.throttle

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive, Directive0 }
import jp.co.tis.lerna.payment.presentation.util.api.ApiId.ApiId
import jp.co.tis.lerna.payment.presentation.util.api._
import jp.co.tis.lerna.payment.presentation.util.directives.throttle.rejection.{
  InactiveApiRejection,
  TooManyRequestsRejection,
}
import jp.co.tis.lerna.payment.utility.tenant.AppTenant

class ThrottleDirective(
    apiThrottling: ApiThrottling,
) {

  def rejectIfApiIsInactive(apiId: ApiId)(implicit tenant: AppTenant): Directive0 = {
    Directive.Empty.tflatMap { _ =>
      apiThrottling.stateOf(apiId) match {
        case Inactive  => reject(InactiveApiRejection(apiId, tenant))
        case _: Active => pass
      }
    }
  }

  def rateLimit(apiId: ApiId)(implicit tenant: AppTenant): Directive0 = {
    Directive.Empty.tflatMap { _ =>
      apiThrottling.stateOf(apiId) match {
        case Nolimit => pass
        case limited: Limited =>
          if (limited.tryAcquire()) pass
          else reject(TooManyRequestsRejection(apiId, tenant))
        case Inactive =>
          // この method は流量制限用なので閉局は別でやるのが自然
          reject(rejection.InactiveApiRejection(apiId, tenant))
      }
    }
  }
}
