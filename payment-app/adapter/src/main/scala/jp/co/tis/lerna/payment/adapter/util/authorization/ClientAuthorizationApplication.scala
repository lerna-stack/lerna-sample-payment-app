package jp.co.tis.lerna.payment.adapter.util.authorization

import jp.co.tis.lerna.payment.adapter.util.authorization.model.{ AuthorizationResult, AuthorizationScope }
import jp.co.tis.lerna.payment.utility.AppRequestContext

import scala.concurrent.Future

trait ClientAuthorizationApplication {
  def authorize(token: String, scopes: Seq[AuthorizationScope])(implicit
      appRequestContext: AppRequestContext,
  ): Future[AuthorizationResult]
}
