package jp.co.tis.lerna.payment.application.readmodelupdater.tagging.salesdetail

import jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.ECPaymentIssuingServiceSalesDetailDomainEvent
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.eventhandler.ECPaymentIssuingServiceEventHandler
import jp.co.tis.lerna.payment.application.readmodelupdater.tagging.EventToTags

/** 決済レコード生成用 EventToTags
  */
object SalesDetailEventToTags extends EventToTags {
  override protected def mapping: PartialFunction[Any, Set[String]] = {
    case _: ECPaymentIssuingServiceSalesDetailDomainEvent =>
      Set(ECPaymentIssuingServiceEventHandler.domainEventTag)

  }
}
