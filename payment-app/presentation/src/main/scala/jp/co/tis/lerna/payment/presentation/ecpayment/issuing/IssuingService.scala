package jp.co.tis.lerna.payment.presentation.ecpayment.issuing

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{ pathPrefix, _ }
import akka.http.scaladsl.server.Route
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.IssuingServiceECPaymentApplication
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.{
  PaymentCancelParameter,
  PaymentParameter,
  SettlementSuccessResponse,
}
import jp.co.tis.lerna.payment.adapter.util.authorization.model.AuthorizationScope
import jp.co.tis.lerna.payment.presentation.ecpayment._
import jp.co.tis.lerna.payment.presentation.ecpayment.issuing.cancel.body.IssuingServiceECPaymentCancelResponse
import jp.co.tis.lerna.payment.presentation.ecpayment.issuing.payment.body.{
  IssuingServiceECPaymentRequest,
  IssuingServiceECPaymentResponse,
}
import jp.co.tis.lerna.payment.presentation.util.api.ApiId
import jp.co.tis.lerna.payment.presentation.util.directives.AuthorizationHeaderDirective
import jp.co.tis.lerna.payment.presentation.util.directives.throttle.ThrottleDirective
import jp.co.tis.lerna.payment.presentation.util.directives.validation.ValidationDirectives
import jp.co.tis.lerna.payment.utility.AppRequestContext

class IssuingService(
    application: IssuingServiceECPaymentApplication,
    auth: AuthorizationHeaderDirective,
    throttleDirective: ThrottleDirective,
) extends ValidationDirectives {
  import throttleDirective._

  private val apiId = ApiId.IssuingService

  def route()(implicit appRequestContext: AppRequestContext): Route =
    pathPrefix("00" / "ec" / "settlements") {
      pathPrefixWalletShopId { walletShopId =>
        pathPrefixOrderId { orderId =>
          concat(
            path("payment") {
              (put & rejectIfApiIsInactive(apiId)) {
                auth.authorize(Seq(AuthorizationScope.OSettlementWrite)).apply {
                  case (customerId, clientId) =>
                    (validEntity(asValid[IssuingServiceECPaymentRequest]) & rateLimit(apiId)) { req =>
                      val param =
                        PaymentParameter(
                          req.amount,
                          walletShopId,
                          orderId,
                          customerId,
                          clientId,
                        )

                      onSuccess(application.pay(param)) { _: SettlementSuccessResponse =>
                        val response = IssuingServiceECPaymentResponse(walletShopId, orderId)
                        complete(StatusCodes.OK -> response)
                      }
                    }

                }
              }
            },
            path("cancel") {
              (put & rejectIfApiIsInactive(apiId)) {
                (auth.authorize(Seq(AuthorizationScope.OSettlementWrite)) & rateLimit(apiId)) {
                  case (customerId, clientId) =>
                    val param = PaymentCancelParameter(walletShopId, orderId, customerId, clientId)

                    onSuccess(application.cancel(param)) { _: SettlementSuccessResponse =>
                      val response = IssuingServiceECPaymentCancelResponse(walletShopId, orderId)
                      complete(StatusCodes.OK -> response)
                    }
                }
              }

            },
          )
        }
      }
    }
}
