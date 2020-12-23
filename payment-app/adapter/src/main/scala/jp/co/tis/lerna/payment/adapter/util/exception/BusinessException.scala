package jp.co.tis.lerna.payment.adapter.util.exception

import jp.co.tis.lerna.payment.adapter.util.OnlineProcessingFailureMessage

/** オンライン処理用の例外 (message と HTTPステータスコードが対応する)
  *
  * @param message
  */
class BusinessException(val message: OnlineProcessingFailureMessage) extends RuntimeException(message.messageContent)
