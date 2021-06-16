package jp.co.tis.lerna.payment.presentation.util.directives.rejection

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ HttpEntity, StatusCodes }
import akka.http.scaladsl.server.{ MalformedRequestContentRejection, MethodRejection, RejectionHandler }
import jp.co.tis.lerna.payment.presentation.util.directives.throttle.rejection.{
  InactiveApiRejection,
  TooManyRequestsRejection,
}
import jp.co.tis.lerna.payment.presentation.util.directives.{ EditorContentTypeDirective, GenLogContextDirective }
import jp.co.tis.lerna.payment.presentation.util.errorhandling.ErrorMessage
import lerna.http.directives.RequestLogDirective
import lerna.log.AppLogging
import spray.json.RootJsonFormat

trait AppRejectionHandler
    extends SprayJsonSupport
    with RequestLogDirective
    with EditorContentTypeDirective
    with GenLogContextDirective
    with AppLogging {
  import spray.json.DefaultJsonProtocol._

  implicit val errorMessageJsonFormat: RootJsonFormat[ErrorMessage] = jsonFormat2(ErrorMessage)
  import akka.http.scaladsl.server.AuthenticationFailedRejection
  import akka.http.scaladsl.server.Directives.complete

  val unauthorized: ErrorMessage = ErrorMessage("指定されたユーザの認可に失敗しました。不正なユーザです。", "CODE-001")

  private[this] val maintenance = ErrorMessage("現在システムメンテナンス中のためご利用頂けません。", "CODE-014")

  private[this] val tooManyRequests = ErrorMessage("アクセスが集中しております。お手数ですが時間をおいて再度お試しください。", "CODE-013")

  def generateValidationMessage(message: String) = ErrorMessage(message, code = "CODE-003")

  implicit def rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case rejection =>
          extractLogContext { implicit logContext =>
            logRequestResultDirective.apply {
              respondWithContentTypeApplicationJsonUTF8 {
                rejection match {
                  case inactiveApiRejection: InactiveApiRejection =>
                    logger.info(s"APIが閉局されていたため拒否しました。 ${inactiveApiRejection.toString}")
                    complete(StatusCodes.ServiceUnavailable -> maintenance)
                  case tooManyRequestsRejection: TooManyRequestsRejection =>
                    logger.info(s"APIごとのレート制限を超えるリクエストを拒否しました。 ${tooManyRequestsRejection.toString}")
                    complete(StatusCodes.ServiceUnavailable -> tooManyRequests)
                  case _: AuthenticationFailedRejection =>
                    complete(StatusCodes.Unauthorized -> unauthorized)
                  case rejection: MalformedRequestContentRejection =>
                    complete(StatusCodes.BadRequest -> generateValidationMessage(rejection.message))
                  case rejection: MethodRejection =>
                    complete(
                      StatusCodes.MethodNotAllowed -> s"HTTP method not allowed, supported methods: ${rejection.supported.name}",
                    )
                  case other =>
                    logger.warn(s"rejection: ${other.toString}")
                    complete(StatusCodes.InternalServerError -> HttpEntity.Empty)
                }
              }
            }
          }
      }.handleNotFound {
        extractLogContext { implicit logContext =>
          logRequestResultDirective.apply {
            respondWithContentTypeApplicationJsonUTF8 { ctx =>
              logger.info("Path: {} does not exist.", ctx.request.uri.toString())
              ctx.complete(StatusCodes.NotFound -> HttpEntity.Empty)
            }
          }
        }
      }.result()

}
