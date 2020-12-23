package jp.co.tis.lerna.payment.presentation.util.directives

import akka.http.scaladsl.model.{ ContentType, HttpCharsets, MediaTypes }
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.directives.BasicDirectives

/** ContentTypeを編集する。
  */
trait EditorContentTypeDirective extends BasicDirectives {
  val `application/json(UTF-8)` : ContentType = MediaTypes.`application/json` withParams Map(
    "charset" -> HttpCharsets.`UTF-8`.value,
  )

  /** レスポンスヘッダーの Content-Type に charset=UTF-8 を入れ
    * application/json; charset=UTF-8
    * とする
    * <br>
    * 理由: デフォルトでUTF-8(※ charset 無し)だが、明示したかったため
    */
  val respondWithContentTypeApplicationJsonUTF8: Directive0 = {
    mapResponseEntity(_.withContentType(`application/json(UTF-8)`))
  }
}
