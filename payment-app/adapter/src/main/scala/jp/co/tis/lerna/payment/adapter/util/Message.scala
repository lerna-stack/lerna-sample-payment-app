package jp.co.tis.lerna.payment.adapter.util

sealed trait Message {
  def messageId: String
  def messageContent: String
}

sealed trait OnlineProcessingFailureMessage  extends Message
sealed trait OfflineProcessingFailureMessage extends Message

// Online: HTTP ステータスコード 4xx で返す
sealed trait ClientErrorMessage extends OnlineProcessingFailureMessage
// Online: HTTP ステータスコード 5xx で返す
sealed trait SystemFailureMessage extends OnlineProcessingFailureMessage

final case class ForbiddenFailure() extends ClientErrorMessage {
  override def messageId      = "CODE-001"
  override def messageContent = "指定されたユーザの認可に失敗しました。不正なユーザです。"
}

final case class NotFound(resourceNm: String) extends ClientErrorMessage {
  override def messageId      = "CODE-002"
  override def messageContent = s"${resourceNm}の取得結果が0件です。"
}

final case class ValidationFailure(reason: String) extends ClientErrorMessage {
  override def messageId      = "CODE-003"
  override def messageContent = s"バリデーションエラー($reason)"
}

final case class PayCompleteNotifyBadRequest(transactionNm: String, errorMessage: String)
    extends OfflineProcessingFailureMessage {
  override def messageId      = "CODE-004"
  override def messageContent = s"${transactionNm}に失敗しました。エラーメッセージ：$errorMessage"
}

final case class TimeOut(transactionNm: String) extends SystemFailureMessage {
  override def messageId      = "CODE-005"
  override def messageContent = s"${transactionNm}でタイムアウトが発生しました。処理を中断します。"
}

final case class IllegalIncentiveRate() extends OfflineProcessingFailureMessage {
  override def messageId: String = "CODE-006"

  override def messageContent: String = s"""複数のキャッシュバックが取得されました。キャッシュバック仮付与額を0で設定します。"""
}

final case class IssuingServiceBadRequestError(transactionNm: String) extends SystemFailureMessage {
  override def messageId      = "CODE-007"
  override def messageContent = s"${transactionNm}の際に、HTTPヘッダの内容が不正です。"
}

final case class IssuingServiceUnavailable(transactionNm: String) extends SystemFailureMessage {
  override def messageId      = "CODE-008"
  override def messageContent = s"${transactionNm}の際に、業務処理にて障害取消が必要なエラーが検知されました。"
}

final case class IssuingServiceServerError(transactionNm: String, errorCode: String = "-")
    extends SystemFailureMessage {
  override def messageId      = "CODE-009"
  override def messageContent = s"${transactionNm}の際に、Issuing Service でのエラーが検知されました。エラーコード：$errorCode"
}

// ログのみ
final case class IssuingServiceTimeoutError(transactionNm: String) extends SystemFailureMessage {
  override def messageId      = "CODE-010"
  override def messageContent = s"${transactionNm}の際に、タイムアウトしました。"
}

final case class IssuingServiceAlreadyCanceled() extends ClientErrorMessage {
  override def messageId      = "CODE-011"
  override def messageContent = "指定された取引が既に取り消し済みになります。"
}

// 未知のエラーの場合
final case class UnpredictableError() extends SystemFailureMessage {
  override def messageId      = "CODE-012"
  override def messageContent = "サーバー内で処理が異常終了しました。"
}
