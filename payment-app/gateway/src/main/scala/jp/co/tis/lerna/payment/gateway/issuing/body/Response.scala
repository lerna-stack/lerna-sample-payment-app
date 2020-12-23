package jp.co.tis.lerna.payment.gateway.issuing.body

import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.IntranId
import lerna.http.json.AnyValJsonFormat
import spray.json.{ DefaultJsonProtocol, JsonFormat, RootJsonFormat }

final case class Response(
    intranid: IntranId, // 取引特定情報
    authId: String,     // 承認番号
    rErrcode: String,   //エラーコード
)

object Response {
  import DefaultJsonProtocol._

  implicit private val intranIdJsonFormat: JsonFormat[IntranId] = AnyValJsonFormat(IntranId.apply, IntranId.unapply)

  implicit val jsonFormat: RootJsonFormat[Response] = jsonFormat3(Response.apply)
}
