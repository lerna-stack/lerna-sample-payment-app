package jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model

// Message type indicator
sealed trait MTI {
  def code: String
  def name: String // ログ表示用
}

case object ShoninUriage extends MTI {
  val code = "0100"
  val name = "承認売上送信"
}

case object ShoninUriageShogaiTorikesi extends MTI {
  val code = "0120"
  val name = "承認売上障害取消"
}

case object ShoninTorikesi extends MTI {
  val code = "0400"
  val name = "承認取消送信"
}
case object ShoninTorikesiShogaiTorieksi extends MTI {
  val code = "0420"
  val name = "承認取消障害取消"
}
