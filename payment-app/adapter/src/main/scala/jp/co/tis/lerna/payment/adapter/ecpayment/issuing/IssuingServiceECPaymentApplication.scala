package jp.co.tis.lerna.payment.adapter.ecpayment.issuing

import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.{
  PaymentCancelParameter,
  PaymentParameter,
  SettlementSuccessResponse,
}
import jp.co.tis.lerna.payment.utility.AppRequestContext

import scala.concurrent.Future

trait IssuingServiceECPaymentApplication {
  def pay(paymentParameter: PaymentParameter)(implicit
      appRequestContext: AppRequestContext,
  ): Future[SettlementSuccessResponse]
  def cancel(paymentCancelParameter: PaymentCancelParameter)(implicit
      appRequestContext: AppRequestContext,
  ): Future[SettlementSuccessResponse]
}
