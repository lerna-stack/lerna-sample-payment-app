package jp.co.tis.lerna.payment.presentation.util.directives.throttle.rejection

import akka.http.scaladsl.server.Rejection
import jp.co.tis.lerna.payment.presentation.util.api.ApiId.ApiId
import lerna.util.tenant.Tenant

/** 機能(API)ごとの流量制限に引っかかったときの Rejection
  *
  * @param apiId API ID
  * @param tenant テナント
  */
final case class TooManyRequestsRejection(apiId: ApiId, tenant: Tenant) extends Rejection
