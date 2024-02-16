package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import akka.actor.ActorRef

sealed trait PekkoSerializationCompatibility {

}

final case class PekkoActorRef(actorRef: ActorRef) extends PekkoSerializationCompatibility
