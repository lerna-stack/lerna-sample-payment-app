package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import akka.actor.ActorRef

sealed trait AkkaSerializationCompatibility

final case class AkkaActorRef(ref: ActorRef) extends AkkaSerializationCompatibility
