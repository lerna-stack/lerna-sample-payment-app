package jp.co.tis.lerna.payment.presentation.util.errorhandling

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.MethodDirectives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.adapter.util._
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import org.scalatest.Inside

class AppExceptionHandlerSpec extends StandardSpec with ScalatestRouteTest with Inside with AppExceptionHandler {
  import AppExceptionHandler._

  "AppExceptionHandler" when {
    "BusinessException" should {

      "NotFound" in {
        val route = get {
          throw new BusinessException(NotFound("userNm"))
        }

        Get() ~> route ~> check {
          expect {
            status === StatusCodes.NotFound
            contentType === `application/json(UTF-8)`
          }
          inside(responseAs[ErrorMessage]) {
            case ErrorMessage(message, code) =>
              expect {
                message === "userNmの取得結果が0件です。"
                code === "CODE-002"
              }
          }
        }
      }

      "ForbiddenFailure" in {
        val route = get {
          throw new BusinessException(ForbiddenFailure())
        }

        Get() ~> route ~> check {
          expect {
            status === StatusCodes.BadRequest
            contentType === `application/json(UTF-8)`
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

      "ValidationFailure" in {
        val route = get {
          throw new BusinessException(ValidationFailure("injected failure"))
        }

        Get() ~> route ~> check {
          expect {
            status === StatusCodes.BadRequest
            contentType === `application/json(UTF-8)`
          }
          inside(responseAs[ErrorMessage]) {
            case ErrorMessage(message, code) =>
              expect {
                message === "バリデーションエラー(injected failure)"
                code === "CODE-003"
              }
          }
        }
      }

      "IssuingServiceAlreadyCanceled" in {
        val route = get {
          throw new BusinessException(IssuingServiceAlreadyCanceled())
        }

        Get() ~> route ~> check {
          expect {
            status === StatusCodes.BadRequest
            contentType === `application/json(UTF-8)`
          }
          inside(responseAs[ErrorMessage]) {
            case ErrorMessage(message, code) =>
              expect {
                message === "指定された取引が既に取り消し済みになります。"
                code === "CODE-011"
              }
          }
        }
      }

      "TimeOut" in {
        val route = get {
          throw new BusinessException(TimeOut("transactionNm"))
        }

        Get() ~> route ~> check {
          expect {
            status === StatusCodes.GatewayTimeout
            contentType === `application/json(UTF-8)`
          }
          inside(responseAs[ErrorMessage]) {
            case ErrorMessage(message, code) =>
              expect {
                message === "transactionNmでタイムアウトが発生しました。処理を中断します。"
                code === "CODE-005"
              }
          }
        }
      }

      "IssuingServiceUnavailable" in {
        val route = get {
          throw new BusinessException(IssuingServiceUnavailable("transactionNm"))
        }

        Get() ~> route ~> check {
          expect {
            status === StatusCodes.ServiceUnavailable
            contentType === `application/json(UTF-8)`
          }
          inside(responseAs[ErrorMessage]) {
            case ErrorMessage(message, code) =>
              expect {
                message === "transactionNmの際に、業務処理にて障害取消が必要なエラーが検知されました。"
                code === "CODE-008"
              }
          }
        }
      }

      "IssuingServiceServerError" in {
        val route = get {
          throw new BusinessException(IssuingServiceServerError("transactionNm", "my-error-code"))
        }

        Get() ~> route ~> check {
          expect {
            status === StatusCodes.InternalServerError
            contentType === `application/json(UTF-8)`
          }
          inside(responseAs[ErrorMessage]) {
            case ErrorMessage(message, code) =>
              expect {
                message === "transactionNmの際に、Issuing Service でのエラーが検知されました。エラーコード：my-error-code"
                code === "CODE-009"
              }
          }
        }
      }

      "IssuingServiceBadRequestError" in {
        val route = get {
          throw new BusinessException(IssuingServiceBadRequestError("transactionNm"))
        }

        Get() ~> route ~> check {
          expect {
            status === StatusCodes.InternalServerError
            contentType === `application/json(UTF-8)`
          }
          inside(responseAs[ErrorMessage]) {
            case ErrorMessage(message, code) =>
              expect {
                message === "transactionNmの際に、HTTPヘッダの内容が不正です。"
                code === "CODE-007"
              }
          }
        }
      }

      "IssuingServiceTimeoutError" in {
        val route = get {
          throw new BusinessException(IssuingServiceTimeoutError("transactionNm"))
        }

        Get() ~> route ~> check {
          expect {
            status === StatusCodes.InternalServerError
            contentType === `application/json(UTF-8)`
          }
          inside(responseAs[ErrorMessage]) {
            case ErrorMessage(message, code) =>
              expect {
                message === "transactionNmの際に、タイムアウトしました。"
                code === "CODE-010"
              }
          }
        }
      }

      "UnpredictableError" in {
        val route = get {
          throw new BusinessException(UnpredictableError())
        }

        Get() ~> route ~> check {
          expect {
            status === StatusCodes.InternalServerError
            contentType === `application/json(UTF-8)`
          }
          inside(responseAs[ErrorMessage]) {
            case ErrorMessage(message, code) =>
              expect {
                message === "サーバー内で処理が異常終了しました。"
                code === "CODE-012"
              }
          }
        }
      }

      "Error" in {
        val route = get {
          throw new Error
        }

        Get() ~> route ~> check {
          expect {
            status === StatusCodes.InternalServerError
          }
          inside(responseAs[ErrorMessage]) {
            case res =>
              expect {
                res.message === "サーバー内で処理が異常終了しました。"
                res.code === "CODE-012"
              }
          }
        }
      }
    }
  }
}
