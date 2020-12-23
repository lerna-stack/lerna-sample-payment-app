package jp.co.tis.lerna.payment.presentation.util.api

import jp.co.tis.lerna.payment.presentation.util.api.ApiId.ApiId
import lerna.util.tenant.Tenant

/** presentation テスト用の制限なし Throttling stub
  */
class NoLimitApiThrottling extends ApiThrottling {
  override def stateOf(apiId: ApiId)(implicit tenant: Tenant): ApiThrottlingState = Nolimit
}
