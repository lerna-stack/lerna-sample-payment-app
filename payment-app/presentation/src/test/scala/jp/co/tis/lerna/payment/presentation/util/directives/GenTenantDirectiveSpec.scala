package jp.co.tis.lerna.payment.presentation.util.directives

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.{ MalformedHeaderRejection, MissingHeaderRejection, Route }
import akka.http.scaladsl.testkit.ScalatestRouteTest
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.Example
import org.scalatest.Inside

class GenTenantDirectiveSpec extends StandardSpec with ScalatestRouteTest with Inside {
  import GenTenantDirectiveSpec.TestRoute

  private val tenantHeaderName = "X-Tenant-Id"

  private def tenantHeader(value: String): RawHeader = RawHeader(tenantHeaderName, value)

  private val testRoute = new TestRoute

  "extractTenantStrict" should {
    val route = testRoute.strictRoute

    "テナントHeaderが存在する（存在するテナント）" when {
      val request = Get("/").withHeaders(tenantHeader(Example.id))
      "テナントを得られる" in {
        request ~> route ~> check {
          expect {
            handled
            responseAs[String] === Example.id
          }
        }
      }
    }

    "テナントHeaderが存在する（存在しないテナント）" when {
      val request = Get("/").withHeaders(tenantHeader("__dummy__"))
      "ログ出力＆rejectされる" in {
        request ~> route ~> check {
          // ログは目視で確認
          inside(rejection) {
            case malformedHeaderRejection: MalformedHeaderRejection =>
              expect(malformedHeaderRejection.headerName === tenantHeaderName)
          }
        }
      }
    }

    "テナントHeaderが存在しない" when {
      val request = Get("/")
      "ログ出力＆rejectされる" in {
        request ~> route ~> check {
          // ログは目視で確認
          inside(rejection) {
            case MissingHeaderRejection(headerName) =>
              expect(headerName === tenantHeaderName)
          }
        }
      }
    }
  }

  "extractTenantOption" should {
    val route = testRoute.optionRoute

    "テナントHeaderが存在する（存在するテナント）" when {
      val request = Get("/").withHeaders(tenantHeader(Example.id))
      "テナントを得られる" in {
        request ~> route ~> check {
          expect {
            handled
            responseAs[String] === Option(Example).toString
          }
        }
      }
    }

    "テナントHeaderが存在する（存在しないテナント）" when {
      val request = Get("/").withHeaders(tenantHeader("__dummy__"))
      "テナントを得られない(がrejectもされない)" in {
        request ~> route ~> check {
          expect {
            handled
            responseAs[String] === None.toString
          }
        }
      }
    }

    "テナントHeaderが存在しない" when {
      val request = Get("/")
      "テナントを得られない(がrejectもされない)" in {
        request ~> route ~> check {
          expect {
            handled
            responseAs[String] === None.toString
          }
        }
      }
    }
  }
}

object GenTenantDirectiveSpec {
  private class TestRoute extends GenTenantDirective {
    val strictRoute: Route = extractTenantStrict { tenant =>
      complete(tenant.id)
    }

    val optionRoute: Route = extractTenantOption { maybeTenant =>
      complete(maybeTenant.toString)
    }
  }
}
