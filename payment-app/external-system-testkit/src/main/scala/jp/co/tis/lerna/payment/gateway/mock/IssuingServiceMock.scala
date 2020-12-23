package jp.co.tis.lerna.payment.gateway.mock

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.google.common.net.HttpHeaders.CONTENT_TYPE
import lerna.testkit.wiremock.ExternalServiceMock

class IssuingServiceMock extends ExternalServiceMock

/** 共通化できるスタブはここに定義
  */
@SuppressWarnings(Array("lerna.warts.NamingVal", "lerna.warts.NamingDef"))
object IssuingServiceMock {
  val apiPath = "/payment"

  val `サンプル：/echoでpongを返す`: MappingBuilder =
    post(urlEqualTo("/echo"))
      .willReturn(
        okJson(""" { "message": "pong" } """),
      )

  def `IS:承認売上要求で成功を返す`: MappingBuilder =
    post(urlEqualTo(apiPath))
      .withRequestBody(matchingJsonPath("mti", equalTo("0100")))
      .willReturn(
        okJson(s"""
                  | {
                  |   "intranid": "471",
                  |   "authId": "741",
                  |   "rErrcode": "00000"
                  | } """.stripMargin),
      )

  // 決済・取消共通
  val `IS:承認売上要求／承認取消要求でBadRequestを返す`: MappingBuilder =
    post(urlEqualTo(apiPath)).willReturn(
      badRequest()
        .withHeader(CONTENT_TYPE, "application/json")
        .withBody(
          """
            | {
            |   "rErrcode": "00001"
            |            }""".stripMargin,
        ),
    )

  // 決済・取消共通
  val `IS:タイムアウトさせる`: MappingBuilder = {
    post(urlEqualTo(apiPath)).willReturn(
      badRequest()
        .withFixedDelay(1000)
        .withHeader(CONTENT_TYPE, "application/json")
        .withBody(
          """
            | {
            |   "intranid": "471",
            |   "authId": "741",
            |   "rErrcode": "00000"
            |            }
          """.stripMargin,
        ),
    )
  }

  // 承認売上、451
  val `IS:承認売上要求でその他のエラーレスポンス(451)` : MappingBuilder =
    post(urlEqualTo(apiPath))
      .withRequestBody(matchingJsonPath("mti", equalTo("0100")))
      .willReturn(status(451))

  // 承認売上、500
  val `IS:承認売上要求でServerErrorを返す(500)` : MappingBuilder =
    post(urlEqualTo(apiPath))
      .withRequestBody(matchingJsonPath("mti", equalTo("0100")))
      .willReturn(serverError)

  // 承認売上、503
  val `IS:承認売上要求でServiceUnavailableを返す(503)` : MappingBuilder =
    post(urlEqualTo(apiPath))
      .withRequestBody(matchingJsonPath("mti", equalTo("0100")))
      .willReturn(serviceUnavailable)

  // ================== 障害取消 ================

  // 承認売上に対する障害取消が失敗、400
  val `IS:承認売上に対する障害取消要求でBadRequestを返す(400)` : MappingBuilder =
    post(urlEqualTo(apiPath))
      .withRequestBody(matchingJsonPath("mti", equalTo("0120")))
      .willReturn(
        badRequest()
          .withHeader(CONTENT_TYPE, "application/json")
          .withBody(
            """
            | {
            |   "intranid": "471",
            |   "rErrcode": "00000"
            | }
          """.stripMargin,
          ),
      )

  // 承認売上に対する障害取消が成功
  def `IS:承認売上に対する障害取消要求で成功を返す`: MappingBuilder =
    post(urlEqualTo(apiPath))
      .withRequestBody(matchingJsonPath("mti", equalTo("0120")))
      .willReturn(
        okJson(s"""
                  | {
                  |   "intranid": "471",
                  |   "rErrcode": "00000"
                  | }
                  | """.stripMargin),
      )

  // 承認売上に対する障害取消が失敗、500
  val `IS:承認売上に対する障害取消要求でServerErrorを返す(500)` : MappingBuilder =
    post(urlEqualTo(apiPath))
      .withRequestBody(matchingJsonPath("mti", equalTo("0120")))
      .willReturn(serverError)

  // 承認売上に対する障害取消が失敗、503
  val `IS:承認売上に対する障害取消要求でServiceUnavailableを返す(503)` : MappingBuilder =
    post(urlEqualTo(apiPath))
      .withRequestBody(matchingJsonPath("mti", equalTo("0120")))
      .willReturn(serviceUnavailable)

  // 承認売上に対する障害取消が失敗、451
  val `IS:承認売上に対する障害取消要求でその他のエラーを返す(451)` : MappingBuilder =
    post(urlEqualTo(apiPath))
      .withRequestBody(matchingJsonPath("mti", equalTo("0120")))
      .willReturn(status(451))

}
