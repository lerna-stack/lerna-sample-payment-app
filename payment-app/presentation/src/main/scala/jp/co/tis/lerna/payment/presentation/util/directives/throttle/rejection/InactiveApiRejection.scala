package jp.co.tis.lerna.payment.presentation.util.directives.throttle.rejection

import akka.http.scaladsl.server.Rejection
import jp.co.tis.lerna.payment.presentation.util.api.ApiId.ApiId
import lerna.util.tenant.Tenant

/** 無効化されているAPIにリクエストが来たときの Rejection
  *
  * @param apiId API ID
  * @param tenant テナント
  */
final case class InactiveApiRejection(apiId: ApiId, tenant: Tenant) extends Rejection
