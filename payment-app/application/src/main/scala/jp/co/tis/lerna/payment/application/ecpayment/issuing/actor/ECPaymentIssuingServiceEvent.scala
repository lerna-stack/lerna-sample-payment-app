package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import java.time.LocalDateTime
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.{ IntranId, SettlementSuccessResponse }
import jp.co.tis.lerna.payment.adapter.issuing.model.{
  AcquirerReversalRequestParameter,
  AuthorizationRequestParameter,
  IssuingServiceResponse,
}
import jp.co.tis.lerna.payment.adapter.util.OnlineProcessingFailureMessage
import jp.co.tis.lerna.payment.adapter.wallet.CustomerId
import jp.co.tis.lerna.payment.application.ecpayment.issuing.IssuingServicePayCredential
import jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.PaymentActor.{ Cancel, Settle }
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.model.{
  MultipleResultTransaction,
  SalesDetailDomainEvent,
}
import lerna.util.trace.TraceId

sealed trait ECPaymentIssuingServiceEvent

sealed trait SettlingResult  extends ECPaymentIssuingServiceEvent
sealed trait CancelingResult extends ECPaymentIssuingServiceEvent

sealed trait ECPaymentIssuingServiceSalesDetailDomainEvent
    extends SalesDetailDomainEvent
    with MultipleResultTransaction
    with ECPaymentIssuingServiceEvent {
  def customerId: CustomerId
}

final case class SettlementAccepted(requestInfo: Settle, systemTime: LocalDateTime)(implicit val traceId: TraceId)
    extends ECPaymentIssuingServiceEvent

final case class SettlementTimeoutDetected()(implicit
    val traceId: TraceId,
) extends ECPaymentIssuingServiceEvent
    with SettlingResult

final case class CancelTimeoutDetected(
)(implicit
    val traceId: TraceId,
) extends ECPaymentIssuingServiceEvent
    with CancelingResult

// 決済成功
final case class SettlementSuccessConfirmed(
    paymentResponse: IssuingServiceResponse,              // IssuingService からのレスポンス
    payCredential: IssuingServicePayCredential,           // RDBMSからの認証情報
    requestInfo: Settle,                                  // presentation -> actorのリクエスト
    successResponse: SettlementSuccessResponse,           // actor -> presentationのレスポンス
    issuingServiceRequest: AuthorizationRequestParameter, // IssuingService へのリクエスト
    systemDate: LocalDateTime,
)(implicit
    val traceId: TraceId,
) extends ECPaymentIssuingServiceEvent
    with SettlingResult
    with ECPaymentIssuingServiceSalesDetailDomainEvent {
  override def customerId: CustomerId = requestInfo.customerId
}

// 決済要求前に失敗
// 非同期処理対象外
final case class SettlementAborted(
    failureMessage: OnlineProcessingFailureMessage,
    systemDate: LocalDateTime,
)(implicit val traceId: TraceId)
    extends ECPaymentIssuingServiceEvent
    with SettlingResult

// 決済失敗
final case class SettlementFailureConfirmed(
    payResponse: Option[IssuingServiceResponse],          // レスポンス 200 の場合、内容格納
    payCredential: IssuingServicePayCredential,           // RDBMSからの認証情報
    requestInfo: Settle,                                  // presentation -> actorのリクエスト
    issuingServiceRequest: AuthorizationRequestParameter, // IssuingService へのリクエスト
    failureMessage: OnlineProcessingFailureMessage,       // 例外情報、IssuingService からの生レスポンス含め場合ある
    systemDate: LocalDateTime,                            // システム日付
)(implicit val traceId: TraceId)
    extends ECPaymentIssuingServiceEvent
    with SettlingResult
    with ECPaymentIssuingServiceSalesDetailDomainEvent {
  override def customerId: CustomerId = requestInfo.customerId
}

final case class CancelAccepted(
    requestInfo: Cancel,          // presentation -> actorのリクエスト
    systemDateTime: LocalDateTime,// システム日時、※決済取消要求時のシステム日時
)(implicit val traceId: TraceId)
    extends ECPaymentIssuingServiceEvent

final case class CancelSuccessConfirmed(
    cancelResponse: IssuingServiceResponse,                             // IssuingService からのレスポンス
    payCredential: IssuingServicePayCredential,                         // 外部システム呼び出しための情報
    requestInfo: Cancel,                                                // 取消のリクエスト情報
    cancelSuccessResponse: SettlementSuccessResponse,                   // クライアントへのレスポンス、unstashされたリクエスト用
    specificDealInfo: IntranId,                                         // 取引特定情報(IssuingService内で取消元取引を一意に識別するID（数字20桁）)
    saleDateTime: LocalDateTime,                                        // 買上日時、※決済要求時のシステム日時
    acquirerReversalRequestParameter: AcquirerReversalRequestParameter, // IssuingService への取消リクエスト
    originalRequest: AuthorizationRequestParameter,                     // IssuingService への元リクエスト
    systemDateTime: LocalDateTime,                                      // システム日時、※決済取消要求時のシステム日時
)(implicit val traceId: TraceId)
    extends ECPaymentIssuingServiceEvent
    with CancelingResult
    with ECPaymentIssuingServiceSalesDetailDomainEvent {
  override def customerId: CustomerId = requestInfo.customerId
}

final case class CancelAborted(
)(implicit val traceId: TraceId)
    extends ECPaymentIssuingServiceEvent
    with CancelingResult

final case class CancelFailureConfirmed(
    paymentResponse: IssuingServiceResponse,                            // IssuingService からのレスポンス
    requestInfo: Cancel,                                                // 取消のリクエスト情報
    payCredential: IssuingServicePayCredential,                         // 外部システム呼び出しための情報
    cancelResponse: Option[IssuingServiceResponse],                     // IssuingService からのレスポンス、障害取消を含める
    failureMessage: OnlineProcessingFailureMessage,                     // クライアントへのレスポンス、unstashされたリクエスト用、障害情報
    originalRequest: AuthorizationRequestParameter,                     // IssuingService への決済リクエスト
    acquirerReversalRequestParameter: AcquirerReversalRequestParameter, // IssuingService への取消リクエスト
    saleDateTime: LocalDateTime,                                        // 買上日時
    systemDateTime: LocalDateTime,                                      // システム日時、※決済取消要求時のシステム日時
)(implicit val traceId: TraceId)
    extends ECPaymentIssuingServiceEvent
    with CancelingResult
    with ECPaymentIssuingServiceSalesDetailDomainEvent {
  override def customerId: CustomerId = requestInfo.customerId
}
