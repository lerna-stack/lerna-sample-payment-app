package jp.co.tis.lerna.payment.gateway.mock

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import lerna.testkit.airframe.DISessionSupport
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.time.{ Seconds, Span }
import spray.json.{ DefaultJsonProtocol, RootJsonFormat }
import wvlet.airframe._

import scala.concurrent.Future

object ExternalServiceMockSpec extends SprayJsonSupport with DefaultJsonProtocol {
  final case class SampleBody(message: String)

  implicit val jsonFormat: RootJsonFormat[SampleBody] = jsonFormat1(SampleBody)
}

@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
// ① *MockSupport を extends
class ExternalServiceMockSpec extends StandardSpec with DISessionSupport with IssuingServiceMockSupport {
  import ExternalServiceMockSpec._

  override protected val diDesign: Design = newDesign
    // ② Mock を DI に登録
    .add(ExternalServiceMockDIDesign.externalServiceMockDesign)
    // ③ Config の baseUrl を上書き
    .bind[Config].toProvider { serviceMock: IssuingServiceMock =>
      ConfigFactory
        .parseString(s"""
                      |jp.co.tis.lerna.payment.gateway {
                      |  issuing.default.base-url = "${serviceMock.server.baseUrl}"
                      |}
        """.stripMargin)
        .withFallback(ConfigFactory.defaultReferenceUnresolved())
        .resolve()
    }
    .bind[ActorSystem].toInstance(ActorSystem())

  "ExternalServiceMock" should {

    implicit val system: ActorSystem = diSession.build[ActorSystem]

    // ④ WireMock の DSL をインポート
    import com.github.tomakehurst.wiremock.client.WireMock._

    val serviceBaseUrl = diSession.build[Config].getString("jp.co.tis.lerna.payment.gateway.issuing.default.base-url")

    import system.dispatcher

    "ダミー応答を返し、受け付けたリクエストの検証を行う" in {

      // ⑤ リクエストを受け付けたときに、どういうレスポンスを返すかを定義
      serviceMock.importStubs(
        IssuingServiceMock.`サンプル：/echoでpongを返す`,
      )

      whenReady(executeSampleGatewayProcess(), Timeout(Span(3, Seconds))) { response =>
        expect {
          response.message === "pong"
        }
      }

      // ⑥ 外部システム（のモック）に送信されたリクエストの検証を行う
      serviceMock.server.verify {
        postRequestedFor(urlEqualTo("/echo"))
          .withRequestBody(matchingJsonPath("message", matching("ping")))
          .withHeader("Content-Type", equalTo("application/json"))
      }
      system.terminate()
    }

    def executeSampleGatewayProcess(): Future[SampleBody] = {
      for {
        entity <- Marshal(SampleBody("ping")).to[RequestEntity]
        response <- {
          val request = HttpRequest(
            HttpMethods.POST,
            uri = Uri(s"$serviceBaseUrl/echo"),
            entity = entity,
          )
          Http().singleRequest(request)
        }
        responseBody <- Unmarshal(response.entity).to[SampleBody]
      } yield responseBody
    }
  }
}
