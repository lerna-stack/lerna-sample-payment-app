package jp.co.tis.lerna.payment.presentation.util.directives.rejection

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import jp.co.tis.lerna.payment.presentation.util.api.ApiId
import jp.co.tis.lerna.payment.presentation.util.directives.throttle.rejection.{
  InactiveApiRejection,
  TooManyRequestsRejection,
}
import jp.co.tis.lerna.payment.presentation.util.errorhandling.ErrorMessage
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.Example
import org.scalatest.Inside

class AppRejectionHandlerSpec extends StandardSpec with ScalatestRouteTest with AppRejectionHandler with Inside {

  "AppRejectionHandler" when {
    "rejection == InactiveApiRejection" should {
      "ServiceUnavailable(503), CODE-014" in {
        val route = reject(InactiveApiRejection(ApiId.BASE, Example))

        Get() ~> Route.seal(route) ~> check {
          expect {
            contentType === `application/json(UTF-8)`
            response.status === StatusCodes.ServiceUnavailable
          }
          val errorMessage = responseAs[ErrorMessage]
          expect {
            errorMessage.code === "CODE-014"
            errorMessage.message === "現在システムメンテナンス中のためご利用頂けません。"
          }
        }
      }
    }

    "rejection == TooManyRequestsRejection" should {
      "ServiceUnavailable(503), CODE-013" in {
        val route = reject(TooManyRequestsRejection(ApiId.BASE, Example))

        Get() ~> Route.seal(route) ~> check {
          expect {
            contentType === `application/json(UTF-8)`
            response.status === StatusCodes.ServiceUnavailable
          }
          val errorMessage = responseAs[ErrorMessage]
          expect {
            errorMessage.code === "CODE-013"
            errorMessage.message === "アクセスが集中しております。お手数ですが時間をおいて再度お試しください。"
          }
        }
      }
    }

    "異常系: AuthenticationFailedRejection" in {
      import akka.http.scaladsl.server._
      val route = Route.seal(reject(AuthenticationFailedRejection(CredentialsRejected, HttpChallenge("JWT", None))))

      Get() ~> route ~> check {
        expect {
          contentType === `application/json(UTF-8)`
          status === StatusCodes.Unauthorized
        }
        inside(responseAs[ErrorMessage]) {
          case ErrorMessage(msg, code) =>
            expect {
              msg === "指定されたユーザの認可に失敗しました。不正なユーザです。"
              code === "CODE-001"
            }
        }
      }
    }

    "異常系: Unknown Rejection" in {
      import akka.http.scaladsl.server._
      final case class UnknownRejection() extends Rejection
      val route = Route.seal(reject(UnknownRejection()))

      Get() ~> route ~> check {
        expect {
          contentType === `application/json(UTF-8)`
          response.status === StatusCodes.InternalServerError
        }
        inside(responseAs[String]) {
          case x =>
            expect { x === "" }
        }
      }
    }

    "異常系: Path NotFound" in {
      import akka.http.scaladsl.server.Route
      val anotherRoute = Route.seal(path("one") {
        complete("two")
      })

      Get() ~> anotherRoute ~> check {
        expect {
          contentType === `application/json(UTF-8)`
          response.status === StatusCodes.NotFound
        }
        inside(responseAs[String]) {
          case x =>
            expect { x === "" }
        }
      }
    }
  }
}
