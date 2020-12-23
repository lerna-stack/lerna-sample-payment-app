package jp.co.tis.lerna.payment.presentation.util.api

import jp.co.tis.lerna.payment.presentation.util.api.ApiId.ApiId
import lerna.util.tenant.Tenant

trait ApiThrottling {
  def stateOf(apiId: ApiId)(implicit tenant: Tenant): ApiThrottlingState
}
