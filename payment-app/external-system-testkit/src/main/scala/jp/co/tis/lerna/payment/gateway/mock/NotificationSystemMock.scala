package jp.co.tis.lerna.payment.gateway.mock

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.{ okJson, _ }
import com.github.tomakehurst.wiremock.http.Fault
import com.google.common.net.HttpHeaders.CONTENT_TYPE
import lerna.testkit.wiremock.ExternalServiceMock

class NotificationSystemMock extends ExternalServiceMock

/** 共通化できるスタブはここに定義
  */
@SuppressWarnings(Array("lerna.warts.NamingVal", "lerna.warts.NamingDef"))
object NotificationSystemMock {

  def `決済履歴管理APIで成功を返す`: MappingBuilder =
    post(urlEqualTo(s"/housemoney/settlement/notices")).willReturn(
      okJson("".stripMargin),
    )

  def `決済履歴管理APIで失敗(400)を返す`: MappingBuilder =
    post(urlEqualTo(s"/housemoney/settlement/notices")).willReturn(
      badRequest()
        .withHeader(CONTENT_TYPE, "application/json")
        .withBody(""" {
                    |  "message": "決済履歴管理APIで失敗(400)を返す",
                    |  "errors": [
                    |  {
                    |    "resource": "/housemoney/settlement/notices",
                    |    "field"  : "walletSettlementId",
                    |    "code"    : "CODE-015"
                    |  },
                    |  {
                    |    "resource": "/housemoney/settlement/notices",
                    |    "field"  : "walletSettlementId",
                    |    "code"    : "CODE-016"
                    |  }
                    | ]
                    | }""".stripMargin),
    )

  def `決済履歴管理APIで失敗(json形式でないもの)を返す`: MappingBuilder =
    post(urlEqualTo(s"/housemoney/settlement/notices")).willReturn(
      badRequest()
        .withHeader(CONTENT_TYPE, "application/json")
        .withBody(""" {
                    |  "message": "決済履歴管理APIで失敗(json形式でないもの)を返す",
                    |  errors: {
                    |    "resource": "/housemoney/settlement/notices",
                    |    "filed"  : "walletSettlementId",
                    |    "code"    : "CODE-015"
                    |  }
                    | }""".stripMargin),
    )

  def `決済履歴管理APIで失敗(500)を返す`: MappingBuilder =
    post(urlEqualTo(s"/housemoney/settlement/notices")).willReturn(
      aResponse().withStatus(500),
    )

  def `決済履歴管理APIでタイムアウト`: MappingBuilder = {
    post(urlEqualTo(s"/housemoney/settlement/notices")).willReturn(
      aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER),
    )
  }

}
