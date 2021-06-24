package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import akka.actor.typed.ActorRef
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.SettlementResponse
import jp.co.tis.lerna.payment.utility.AppRequestContext

final case class ProcessingContext(
    replyTo: ActorRef[SettlementResponse],
)(implicit val appRequestContext: AppRequestContext)
