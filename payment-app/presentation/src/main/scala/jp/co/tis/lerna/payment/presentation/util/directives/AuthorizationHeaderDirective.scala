package jp.co.tis.lerna.payment.presentation.util.directives

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.BasicDirectives.extractExecutionContext
import akka.http.scaladsl.server.directives.Credentials
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.util.ForbiddenFailure
import jp.co.tis.lerna.payment.adapter.util.authorization.ClientAuthorizationApplication
import jp.co.tis.lerna.payment.adapter.util.authorization.model.AuthorizationScope
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.adapter.wallet.{ ClientId, CustomerId }
import jp.co.tis.lerna.payment.utility.AppRequestContext
import slick.util.Logging

import scala.concurrent.{ ExecutionContext, Future }

trait AuthorizationHeaderDirective {
  def authorize(scopes: Seq[AuthorizationScope])(implicit
      appRequestContext: AppRequestContext,
  ): Directive1[(CustomerId, ClientId)]
}

class AuthorizationHeaderDirectiveImpl(
    clientAuthorizationApp: ClientAuthorizationApplication,
    config: Config,
) extends Logging
    with AuthorizationHeaderDirective {

  private def authenticate(credentials: Credentials, scopes: Seq[AuthorizationScope])(implicit
      ec: ExecutionContext,
      appRequestContext: AppRequestContext,
  ): Future[Option[(CustomerId, ClientId)]] = {
    credentials match {
      case Credentials.Provided(token) =>
        clientAuthorizationApp
          .authorize(token, scopes).map { authorizationResult =>
            val customerId = CustomerId.from(authorizationResult.subject)
            val clientId   = authorizationResult.clientId

            Option((customerId, clientId))
          }
      case Credentials.Missing =>
        Future.successful(None)
    }
  }

  override def authorize(
      scopes: Seq[AuthorizationScope],
  )(implicit appRequestContext: AppRequestContext): Directive1[(CustomerId, ClientId)] = {
    extractExecutionContext
      .flatMap { implicit ec =>
        if (scopes.isEmpty)
          throw new BusinessException(ForbiddenFailure())
        authenticateOAuth2Async("", authenticate(_, scopes))
      }
  }
}
