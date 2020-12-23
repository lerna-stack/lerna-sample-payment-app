package jp.co.tis.lerna.payment.application.ecpayment.issuing

import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.TransactionId
import lerna.util.tenant.Tenant

import scala.concurrent.Future

// 取引ID採番
trait TransactionIdFactory {
  def generate()(implicit tenant: Tenant): Future[TransactionId]
}
