package jp.co.tis.lerna.payment.presentation.management

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.presentation.PresentationDIDesign
import jp.co.tis.lerna.payment.presentation.management.mock.MetricsImplMock
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import lerna.management.stats.Metrics
import lerna.testkit.airframe.DISessionSupport
import org.scalatest.{ Inside, WordSpec }
import wvlet.airframe.Design

@SuppressWarnings(
  Array(
    "lerna.warts.Awaits",
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
  ),
)
class MetricsRouteSpec extends WordSpec with StandardSpec with ScalatestRouteTest with Inside with DISessionSupport {

  override protected val diDesign: Design = PresentationDIDesign.presentationDesign
    .bind[ActorSystem].toProvider { config: Config =>
      ActorSystem("MetricsImplSpec", config)
    }
    .bind[Metrics].to[MetricsImplMock]
    .bind[Config].toInstance(ConfigFactory.load())

  private val route = diSession.build[MetricsRoute].route

  "MetricsRoute" when {
    "正常系: jvm_heap_used" in {

      val request = Get("/metrics/jvm_heap_used")

      request ~> route ~> check {
        expect {
          status === StatusCodes.OK
          responseAs[String] === "111111"
        }
      }
    }

    "正常系: jvm_heap_max" in {

      val request = Get("/metrics/jvm_heap_max")

      request ~> route ~> check {
        expect {
          status === StatusCodes.OK
          responseAs[String] === "222222"
        }
      }
    }

    "異常系: 設定されていないメトリクスには 404 を返す" in {

      val request = Get("/metrics/invalid/value")

      request ~> route ~> check {
        expect {
          status === StatusCodes.NotFound
          responseAs[String].isEmpty
        }
      }
    }

    "異常系: 存在しない tenant を指定した場合 400 BadRequest を返す" in {
      val request = Get("/metrics/dummy?tenant=dummy-tenant")

      request ~> route ~> check {
        expect {
          status === StatusCodes.BadRequest
          responseAs[String] === """tenant: "dummy-tenant" is non-existent"""
        }
      }
    }
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}
