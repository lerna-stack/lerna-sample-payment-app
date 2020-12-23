package jp.co.tis.lerna.payment.presentation.util.errorhandling

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.ExceptionHandler
import jp.co.tis.lerna.payment.adapter.util._
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.presentation.util.directives.{ EditorContentTypeDirective, GenLogContextDirective }
import lerna.http.directives.RequestLogDirective
import lerna.log.AppLogging
import spray.json.RootJsonFormat

import scala.util.control.NonFatal

object AppExceptionHandler extends SprayJsonSupport {
  import spray.json.DefaultJsonProtocol._

  implicit val errorJsonFormat: RootJsonFormat[ErrorInfo]           = jsonFormat3(ErrorInfo)
  implicit val errorMessageJsonFormat: RootJsonFormat[ErrorMessage] = jsonFormat2(ErrorMessage)
}

trait AppExceptionHandler
    extends AppLogging
    with RequestLogDirective
    with EditorContentTypeDirective
    with GenLogContextDirective {
  import AppExceptionHandler._

  implicit def commonExceptionHandlerWithLogging: ExceptionHandler =
    commonExceptionHandler
      .andThen(respondWithContentTypeApplicationJsonUTF8(_))
      .andThen(extractLogContext.flatMap(logRequestResultDirective(_)).apply(_))

  // バリデーションエラーのレスポンスは`ValidationDirectives`にて実施するためここでは実装しない
  def commonExceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: BusinessException =>
      val statusCode: StatusCode = ex.message match {
        case message: ClientErrorMessage   => getStatusCodeFrom(message)
        case message: SystemFailureMessage => getStatusCodeFrom(message)
      }
      val message = ex.message
      complete {
        statusCode -> ErrorMessage(
          message.messageContent,
          message.messageId,
        )
      }

    case NonFatal(cause) =>
      val response = ErrorMessage(
        code = "CODE-012",
        message = "サーバー内で処理が異常終了しました。",
      )
      extractLogContext { implicit appRequestContext =>
        logger.warn(cause, response.message)
        complete {
          StatusCodes.InternalServerError -> response
        }
      }
  }

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  def getStatusCodeFrom(message: ClientErrorMessage): StatusCodes.ClientError = {
    message match {
      case _: NotFound =>
        StatusCodes.NotFound
      case _: ForbiddenFailure =>
        StatusCodes.Forbidden
      case _: ValidationFailure =>
        StatusCodes.BadRequest
      case _: IssuingServiceAlreadyCanceled =>
        StatusCodes.BadRequest
    }
  }

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  def getStatusCodeFrom(message: SystemFailureMessage): StatusCodes.ServerError = {
    message match {
      case _: TimeOut =>
        StatusCodes.GatewayTimeout
      case _: IssuingServiceUnavailable =>
        StatusCodes.ServiceUnavailable
      case _: IssuingServiceServerError =>
        StatusCodes.InternalServerError
      case _: IssuingServiceBadRequestError =>
        StatusCodes.InternalServerError
      case _: IssuingServiceTimeoutError =>
        StatusCodes.InternalServerError

      case _: UnpredictableError =>
        StatusCodes.InternalServerError
    }
  }
}
