package jp.co.tis.lerna.payment.presentation.util.api

/** API の throttle設定
  */
sealed trait ApiThrottlingState

/** 閉局しているAPI
  */
case object Inactive extends ApiThrottlingState

/** 開局しているAPI
  */
sealed trait Active extends ApiThrottlingState

/** Limit 無しのAPI
  */
case object Nolimit extends Active

/** 流量制限されたAPI
  */
trait Limited extends Active {
  def tryAcquire(): Boolean
}
