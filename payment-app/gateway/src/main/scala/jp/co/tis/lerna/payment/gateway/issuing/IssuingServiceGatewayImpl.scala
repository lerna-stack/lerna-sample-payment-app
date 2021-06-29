package jp.co.tis.lerna.payment.gateway.issuing

import akka.actor.Scheduler
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.TypedSchedulerOps
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.{
  MTI,
  ShoninTorikesiShogaiTorieksi,
  ShoninUriage,
  ShoninUriageShogaiTorikesi,
}
import jp.co.tis.lerna.payment.adapter.issuing.IssuingServiceGateway
import jp.co.tis.lerna.payment.adapter.issuing.model.{
  AcquirerReversalRequestParameter,
  AuthorizationRequestParameter,
  IssuingServiceResponse,
}
import jp.co.tis.lerna.payment.adapter.util._
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.gateway.issuing.body.Request
import jp.co.tis.lerna.payment.utility.AppRequestContext
import lerna.http.HttpRequestLoggingSupport
import lerna.log.AppLogging

import java.time.format.DateTimeFormatter
import scala.concurrent.{ Future, TimeoutException }
import scala.util.{ Failure, Success }

/** Issuing Service Gateway の実装
  *
  * @param config　設定ファイル
  * @param system actorシステム
  */
class IssuingServiceGatewayImpl(config: Config)(implicit val system: ActorSystem[Nothing])
    extends IssuingServiceGateway
    with SprayJsonSupport
    with HttpRequestLoggingSupport
    with AppLogging {
  private implicit val scheduler: Scheduler = system.scheduler.toClassic
  import system.executionContext

  private val issuingServiceConfig = new IssuingServiceConfig(config)
  private val transNmFailureCancel = "障害取消送信"

  /**  IssuingService へ承認／取消要求を送信する。ある障害の場合、障害取消も行う。
    * @param req　リクエスト
    * @return レスポンス(JSON情報付き)
    */
  private def sendRequest(
      req: Request,
  )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] = {
    val paymentResult = sendPayOrCancelRequest(req).map(_.map { res =>
      IssuingServiceResponse(
        intranid = res.intranid,
        authId = res.authId,
        rErrcode = res.rErrcode,
      )
    })

    paymentResult transformWith {
      case Failure(_)                                  => retrySendFailureCancel(req).map(Left.apply)
      case Success(Left(_: IssuingServiceUnavailable)) => retrySendFailureCancel(req).map(Left.apply)
      case Success(other)                              => Future.successful(other)
    } transform (_.flatMap(_.left.map(message => new BusinessException(message)).toTry))
  }

  private def param2request(parameter: AuthorizationRequestParameter): Request =
    Request(
      mti = ShoninUriage,                                                                      // メッセージタイプ(MTI)
      pan = parameter.pan,                                                                     // カード番号
      amountTran = parameter.amountTran,                                                       // 取引金額
      tranDateTime = parameter.tranDateTime.format(DateTimeFormatter.ofPattern("MMddHHmmss")), // 送信日時
      transactionId = parameter.transactionId,                                                 // 取引ID
      accptrId = parameter.accptrId,                                                           // 加盟店ID 左詰め１５桁固定長
      paymentId = parameter.paymentId,                                                         // (会員ごと)決済番号
      originalPaymentId = None,                                                                // 元(会員ごと)決済番号
      terminalId = parameter.terminalId,                                                       // 端末識別番号
    )

  override def requestAuthorization(
      parameter: AuthorizationRequestParameter,
  )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] = {
    val req: Request = param2request(parameter)
    sendRequest(req)
  }

  override def requestAcquirerReversal(
      parameter: AcquirerReversalRequestParameter,
      originalRequestParameter: AuthorizationRequestParameter,
  )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] = {
    val originalRequest = param2request(originalRequestParameter)
    val req = originalRequest.copy(
      mti = parameter.MessageTypeIndicator,                  // メッセージタイプ(MTI) 承認取消
      transactionId = parameter.transactionId,               // 取引ID、採番
      paymentId = parameter.paymentId,                       // (会員ごと)決済番号
      originalPaymentId = Option(originalRequest.paymentId), // 元取引(会員ごと)決済番号
      terminalId = parameter.terminalId,                     // 端末識別番号
    )
    sendRequest(req)
  }

  /**   承認売上/取消要求リクエスト送信
    *
    * @param request リクエスト
    * @return 結果
    */
  def sendPayOrCancelRequest(
      request: Request,
  )(implicit appRequestContext: AppRequestContext): Future[Either[SystemFailureMessage, body.Response]] = {
    val res = for {
      entity <- Marshal(request).to[RequestEntity]
      httpResponse <- {
        val httpRequest = HttpRequest(
          method = HttpMethods.POST,
          uri = issuingServiceConfig.url,
          entity = entity,
        )

        httpSingleRequestWithAroundLogWithTimeout(
          req = httpRequest,
          timeout = issuingServiceConfig.responseTimeout,
          maskLog = _.replaceAll("""("pan"\s*:\s*")[^"]+""", "$1********"),
        )
      }
      result <- httpResponse.status match {
        // 「承認売上／取消要求」に対する応答のステータスが「200(OK)」場合、「(6) 決済履歴一時登録」処理へ。
        case StatusCodes.OK =>
          for {
            payResponse <- Unmarshal(httpResponse.entity).to[body.Response]
          } yield {
            Right(payResponse)
          }

        // 「承認売上／取消要求」に対する応答のステータスが「400(Bad Request)」場合、ワーニングログを出力して「(6) 決済履歴一時登録」処理へ。
        case StatusCodes.BadRequest =>
          httpResponse.discardEntityBytes()
          val message: SystemFailureMessage = IssuingServiceBadRequestError(request.mti.name)
          logger.warn(s"${message.messageId}: ${message.messageContent}")
          Future.successful(Left(message))

        // 「承認売上／取消要求」に対する応答のステータスが「500～599」場合、ワーニングログを出力して「(5) 障害取消送信」処理へ。
        case _: StatusCodes.ServerError =>
          httpResponse.discardEntityBytes()
          val message: SystemFailureMessage = IssuingServiceUnavailable(request.mti.name)
          logger.warn(s"${message.messageId}: ${message.messageContent}")
          logger.warn(
            s"Issuing Service からのレスポンスのStatusCodes が ServerError(${httpResponse.status.toString()}) のため障害取消します",
          )
          Future.successful(Left(message))

        // 上記以外の場合、ワーニングログを出力して「(6) 決済履歴一時登録」処理へ。
        case _ =>
          httpResponse.discardEntityBytes()
          val message: SystemFailureMessage = IssuingServiceServerError(request.mti.name)
          logger.warn(s"${message.messageId}: ${message.messageContent} ${httpResponse.status.toString()}")
          Future.successful(Left(message))
      }

    } yield result

    res andThen {
      case Failure(e: TimeoutException) =>
        val message = IssuingServiceTimeoutError(request.mti.name)
        logger.warn(e, s"${message.messageId}: ${message.messageContent}")
      case Failure(e: Throwable) =>
        // 想定外のエラー
        val message = IssuingServiceServerError(request.mti.name)
        logger.warn(e, s"${message.messageId}: ${message.messageContent}")
    }
  }

  /** リトライ：障害取消送信
    *
    * @param payRequestParams リクエスト
    * @return 例外または成功のレスポンス
    */
  private def retrySendFailureCancel(
      payRequestParams: Request,
  )(implicit appRequestContext: AppRequestContext): Future[SystemFailureMessage] = {

    val mti: MTI = payRequestParams.mti match {
      case ShoninUriage => ShoninUriageShogaiTorikesi /* 承認売上の障害取消 */
      case _            => ShoninTorikesiShogaiTorieksi /* 承認取消の障害取消 */
    }

    // 承認売り上げの場合、リクエストの項目が一緒なので、型共通
    val failureCancelRequest = payRequestParams.copy(mti = mti)

    akka.pattern.retry(
      attempt = () => {
        logger.info("障害取消をします。")
        sendFailureCancel(failureCancelRequest)
      },
      attempts = issuingServiceConfig.retryAttempts,
      delay = issuingServiceConfig.retryDelay,
    ) recover {
      // ①上限に到達したときにタイムアウトが発生していた場合
      case ex: TimeoutException =>
        val message: SystemFailureMessage = TimeOut(transNmFailureCancel)
        logger.warn(ex, s"${message.messageId}: ${message.messageContent}")
        message

      //  上限に到達したときに5xxの場合
      case ex: BusinessException =>
        val message: SystemFailureMessage = IssuingServiceServerError(transNmFailureCancel)
        logger.warn(s"${message.messageId}: ${message.messageContent}")
        message

      // ②上限に到達したときに上記以外の場合
      case ex =>
        val message: SystemFailureMessage = IssuingServiceServerError(payRequestParams.mti.name)
        logger.warn(ex, s"${message.messageId}: ${message.messageContent}")
        message
    }
  }

  /**  障害取消送信
    *
    * @param failureCancelRequest 障害取消リクエスト
    * @return 例外または成功のレスポンス
    */
  private def sendFailureCancel(
      failureCancelRequest: Request,
  )(implicit appRequestContext: AppRequestContext): Future[SystemFailureMessage] = {
    val res = for {
      requestEntity <- Marshal(failureCancelRequest).to[RequestEntity]
      httpResponse <- {
        val httpRequest = HttpRequest(
          method = HttpMethods.POST,
          uri = issuingServiceConfig.url,
          entity = requestEntity,
        )

        httpSingleRequestWithAroundLogWithTimeout(
          req = httpRequest,
          timeout = issuingServiceConfig.responseTimeout,
          maskLog = _.replaceAll("""("pan"\s*:\s*")[^"]+""", "$1********"),
        )
      }
    } yield {
      httpResponse.discardEntityBytes()
      httpResponse.status match {
        // 障害取消要求に対する応答のステータスが「200(OK)」場合、「(6) 決済履歴一時登録」処理へ。
        case StatusCodes.OK =>
          val message: SystemFailureMessage = IssuingServiceUnavailable(transNmFailureCancel)
          logger.warn(s"${message.messageId}: ${message.messageContent}")
          message

        // 障害取消要求に対する応答のステータスが「400(Bad Request)」場合、ワーニングログを出力して「(6) 決済履歴一時登録」処理へ。
        case StatusCodes.BadRequest =>
          val message: SystemFailureMessage = IssuingServiceBadRequestError(transNmFailureCancel)
          logger.warn(s"${message.messageId}: ${message.messageContent}")
          message

        // 障害取消要求に対する応答のステータスが「500～599」場合、ワーニングログを出力して「(5) 障害取消送信」処理へ。
        case _: StatusCodes.ServerError => //500～599
          val message = IssuingServiceUnavailable(transNmFailureCancel)
          logger.warn(s"${message.messageId}: ${message.messageContent}")
          logger.warn(s"Issuing Service からのレスポンスのStatusCodes が ServerError(${httpResponse.status.toString()})")
          throw new BusinessException(message)

        // 上記以外の場合、ワーニングログを出力して「(6) 決済履歴一時登録」処理へ。
        case _ =>
          val message: SystemFailureMessage = IssuingServiceServerError(transNmFailureCancel)
          logger.warn(s"${message.messageId}: ${message.messageContent}")
          logger.warn(s"障害取消に失敗しました(StatusCode == ${httpResponse.status.toString()})")
          message
      }
    }

    // タイムアウトログ出力処理
    res andThen {
      case Failure(e: TimeoutException) =>
        val message = IssuingServiceTimeoutError(transNmFailureCancel)
        logger.warn(e, s"${message.messageId}: ${message.messageContent}")
    }
  }
}
