package jp.co.tis.lerna.payment.application.util.authorization

import jp.co.tis.lerna.payment.adapter.util.authorization.ClientAuthorizationApplication
import jp.co.tis.lerna.payment.adapter.util.authorization.model.{ AuthorizationResult, AuthorizationScope, Subject }
import jp.co.tis.lerna.payment.adapter.wallet.ClientId
import jp.co.tis.lerna.payment.utility.AppRequestContext

import scala.concurrent.Future

class ClientAuthorizationApplicationImpl() extends ClientAuthorizationApplication {

  override def authorize(token: String, scopes: Seq[AuthorizationScope])(implicit
      appRequestContext: AppRequestContext,
  ): Future[AuthorizationResult] = {
    // TODO: 認可の仕組みに合わせて実装する
    val subject  = Subject("dummy")
    val clientId = ClientId(0)
    val result   = AuthorizationResult(subject, clientId)
    Future.successful(result)
  }

}
