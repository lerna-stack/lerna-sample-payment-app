package jp.co.tis.lerna.payment.presentation.util.directives.throttle

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import jp.co.tis.lerna.payment.presentation.util.api._
import jp.co.tis.lerna.payment.presentation.util.directives.throttle.rejection.{
  InactiveApiRejection,
  TooManyRequestsRejection,
}
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant._
import org.mockito
import org.scalatest.Inside

@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
class ThrottleDirectiveSpec
    extends StandardSpec
    with ScalatestRouteTest
    with Inside
    with mockito.MockitoSugar
    with mockito.ArgumentMatchersSugar
    with mockito.scalatest.ResetMocksAfterEachTest {

  private implicit val tenant: AppTenant = Example

  private val apiId = ApiId.BASE

  "rejectIfApiIsInactive()" when {
    "APIは閉局している" should {
      val throttlingMock = mock[ApiThrottling]
      when(throttlingMock.stateOf(eqTo(apiId))(eqTo(tenant))).thenReturn(Inactive)

      val directive = new ThrottleDirective(throttlingMock)
      val route: Route = (pass & directive.rejectIfApiIsInactive(apiId)) {
        complete("")
      }

      "リクエストは拒否される" in {
        Get() ~> route ~> check {
          inside(rejection) {
            case inactiveApiRejection: InactiveApiRejection =>
              expect {
                inactiveApiRejection.apiId === apiId
                inactiveApiRejection.tenant === tenant
              }
          }
        }
      }
    }

    "APIは開局している" should {
      val throttlingMock = mock[ApiThrottling]
      when(throttlingMock.stateOf(eqTo(apiId))(eqTo(tenant))).thenReturn(Nolimit)

      val directive = new ThrottleDirective(throttlingMock)
      val route: Route = (pass & directive.rejectIfApiIsInactive(apiId)) {
        complete("")
      }

      "リクエストは許可される" in {
        Get() ~> route ~> check {
          expect(handled)
        }
      }
    }
  }

  "rateLimit()" when {
    "APIは閉局している" should {
      val throttlingMock = mock[ApiThrottling]
      when(throttlingMock.stateOf(eqTo(apiId))(eqTo(tenant))).thenReturn(Inactive)

      val directive = new ThrottleDirective(throttlingMock)
      val route: Route = (pass & directive.rateLimit(apiId)) {
        complete("")
      }

      "リクエストは拒否される" in {
        Get() ~> route ~> check {
          inside(rejection) {
            case inactiveApiRejection: InactiveApiRejection =>
              expect {
                inactiveApiRejection.apiId === apiId
                inactiveApiRejection.tenant === tenant
              }
          }
        }
      }
    }

    "APIは開局している" when {
      "流量制限はOFF" should {
        val throttlingMock = mock[ApiThrottling]
        when(throttlingMock.stateOf(eqTo(apiId))(eqTo(tenant))).thenReturn(Nolimit)

        val directive = new ThrottleDirective(throttlingMock)
        val route: Route = (pass & directive.rateLimit(apiId)) {
          complete("")
        }

        "リクエストは許可される" in {
          Get() ~> route ~> check {
            expect(handled)
          }
        }
      }

      "流量制限はON" when {
        "流量をpass" should {
          val throttlingMock = mock[ApiThrottling]
          when(throttlingMock.stateOf(eqTo(apiId))(eqTo(tenant))).thenReturn(new Limited {
            override def tryAcquire(): Boolean = true
          })

          val directive = new ThrottleDirective(throttlingMock)
          val route: Route = (pass & directive.rateLimit(apiId)) {
            complete("")
          }
          "リクエストは許可される" in {
            Get() ~> route ~> check {
              expect(handled)
            }
          }
        }

        "流量制限に引っかかった" should {
          val throttlingMock = mock[ApiThrottling]
          when(throttlingMock.stateOf(eqTo(apiId))(eqTo(tenant))).thenReturn(new Limited {
            override def tryAcquire(): Boolean = false
          })

          val directive = new ThrottleDirective(throttlingMock)
          val route: Route = (pass & directive.rateLimit(apiId)) {
            complete("")
          }
          "リクエストは拒否される" in {
            Get() ~> route ~> check {
              inside(rejection) {
                case tooManyRequestsRejection: TooManyRequestsRejection =>
                  expect {
                    tooManyRequestsRejection.apiId === apiId
                    tooManyRequestsRejection.tenant === tenant
                  }
              }
            }
          }
        }
      }
    }
  }
}
