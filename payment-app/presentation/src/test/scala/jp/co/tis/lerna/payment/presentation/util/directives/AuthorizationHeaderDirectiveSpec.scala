package jp.co.tis.lerna.payment.presentation.util.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{ Authorization, OAuth2BearerToken }
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.adapter.util.ForbiddenFailure
import jp.co.tis.lerna.payment.adapter.util.authorization.ClientAuthorizationApplication
import jp.co.tis.lerna.payment.adapter.util.authorization.model.{ AuthorizationResult, AuthorizationScope, Subject }
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.adapter.wallet.ClientId
import jp.co.tis.lerna.payment.presentation.util.errorhandling.{ AppExceptionHandler, ErrorMessage }
import jp.co.tis.lerna.payment.utility.AppRequestContext
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.Example
import lerna.util.trace.TraceId
import org.scalatest.Inside
import wvlet.airframe._

import scala.concurrent.Future

class ClientAuthorizationApplicationMock extends ClientAuthorizationApplication {
  override def authorize(token: String, scopes: Seq[AuthorizationScope])(implicit
      appRequestContext: AppRequestContext,
  ): Future[AuthorizationResult] = {
    token match {
      case "token"     => Future.successful(AuthorizationResult(Subject("12345"), ClientId(67890)))
      case "Forbidden" => Future.failed(new BusinessException(ForbiddenFailure()))
    }
  }
}

class TestRoute(auth: AuthorizationHeaderDirective) {
  private implicit val appRequestContext: AppRequestContext = AppRequestContext(TraceId("12345"), tenant = Example)
  lazy val route: Route = auth.authorize(Seq(AuthorizationScope.WSettlementWrite)).apply { _ =>
    complete(StatusCodes.OK)
  }
}

// Lint回避のため
@SuppressWarnings(
  Array(
    "lerna.warts.Awaits",
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
class AuthorizationHeaderDirectiveSpec
    extends StandardSpec
    with ScalatestRouteTest
    with Inside
    with AppExceptionHandler {
  import AppExceptionHandler._
  override def beforeAll(): Unit = {
    session.start
  }

  val design: Design = newDesign
    .bind[ClientAuthorizationApplication].to[ClientAuthorizationApplicationMock]
    .bind[AuthorizationHeaderDirective].to[AuthorizationHeaderDirectiveImpl]
    .bind[TestRoute].toSingleton
    .bind[Config].toInstance(ConfigFactory.load)

  val session: Session = design.newSession

  "AuthorizationHeaderDirective" when {
    lazy val route: Route = session.build[TestRoute].route

    "異常系: tokenが適切なものではなかった Forbidden" in {
      val request =
        Post("/").addHeader(Authorization(OAuth2BearerToken("Forbidden")))
      request ~> Route.seal(route) ~> check {
        expect {
          status === StatusCodes.Forbidden
        }
        inside(responseAs[ErrorMessage]) {
          case ErrorMessage(message, code) =>
            expect {
              message === "指定されたユーザの認可に失敗しました。不正なユーザです。"
              code === "CODE-001"
            }
        }
      }
    }

  }

  override def afterAll(): Unit = {
    session.close()
  }
}
