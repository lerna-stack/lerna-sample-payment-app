package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

//import akka.actor.ActorRef
//import akka.actor.typed.{ActorRef => TypedActorRef}
//import akka.util.ByteString
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.typed.{ ActorRef => TypedActorRef }
import org.apache.pekko.util.ByteString

sealed trait SerializationCompatibility

final case class ActorRefSerialize(ref: ActorRef) extends SerializationCompatibility

final case class TypedActorRefSerialize[T](ref: TypedActorRef[T]) extends SerializationCompatibility

final case class ByteStringSerialize(bStr: ByteString) extends SerializationCompatibility
