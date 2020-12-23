package jp.co.tis.lerna.payment.application.ecpayment.issuing

import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.PaymentId
import jp.co.tis.lerna.payment.adapter.wallet.CustomerId
import lerna.util.tenant.Tenant

import scala.concurrent.Future

// (会員ごと)決済番号採番
trait PaymentIdFactory {
  def generateIdFor(customerId: CustomerId)(implicit tenant: Tenant): Future[PaymentId]
}
