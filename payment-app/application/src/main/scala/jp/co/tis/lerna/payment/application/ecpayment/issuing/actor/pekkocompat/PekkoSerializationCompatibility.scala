package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.pekkocompat

import org.apache.pekko.actor.ActorRef


sealed trait PekkoSerializationCompatibility

final case class ActorRefSerialize(actorRef: ActorRef) extends PekkoSerializationCompatibility
