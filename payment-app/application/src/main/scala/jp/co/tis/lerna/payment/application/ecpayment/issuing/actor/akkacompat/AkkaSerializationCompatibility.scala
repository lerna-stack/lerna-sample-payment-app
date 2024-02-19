package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.akkacompat

import akka.actor.ActorRef

sealed trait AkkaSerializationCompatibility

final case class ActorRefSerialize(ref: ActorRef) extends AkkaSerializationCompatibility
