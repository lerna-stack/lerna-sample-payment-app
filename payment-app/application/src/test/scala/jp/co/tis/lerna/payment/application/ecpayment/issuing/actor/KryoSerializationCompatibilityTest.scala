package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import jp.co.tis.lerna.payment.utility.scalatest.SpecAssertions
import org.scalatest.funsuite.AnyFunSuiteLike
import akka.serialization.{SerializationExtension => AkkaSerializationExtension}
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit => PekkoScalaTestWithActorTestKit}
import pekko.serialization.{SerializationExtension => PekkoSerializationExtension}

import java.nio.charset.StandardCharsets

class KryoSerializationCompatibilityTest

class AkkaSerializationTest extends ScalaTestWithActorTestKit() with SpecAssertions with AnyFunSuiteLike {

  import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }

  private val akkaSerializationExtension = AkkaSerializationExtension(system)

  test("Serialize ActorRef in Akka") {
    val s        = ActorSystem("PekkoSerializationTest")
    val actorRef = s.actorOf(Foo.props(), "foo")
    val event    = akkacompat.ActorRefSerialize(actorRef)
    val bytes = akkaSerializationExtension.serialize(event).get
    println(bytes.mkString(","))
    println(new String(bytes, StandardCharsets.UTF_8))
    val serializerId = akkaSerializationExtension.findSerializerFor(event).identifier
    val manifest = classOf[akkacompat.ActorRefSerialize].getName
    val deserialized = akkaSerializationExtension.deserialize(bytes, serializerId, manifest).get
    expect(event === deserialized)
  }

  object Foo {
    final case class Message(content: String)

    def props(): Props = Props(new Foo)
  }

  class Foo extends Actor with ActorLogging {
    import Foo._

    override def receive: Receive = {
      case Message(content) => log.info(content)
    }
  }
}

class PekkoSerializationTest extends PekkoScalaTestWithActorTestKit() with SpecAssertions with AnyFunSuiteLike {
  import pekko.actor.{ Actor, ActorLogging, ActorSystem, Props }

  private val pekkoSerializationExtension = PekkoSerializationExtension(system)

  test("Deserialize ActorRef in Pekko") {
    val actorSystem        = ActorSystem("foobazbar")
    val actorRef = actorSystem.actorOf(Foo.props(), "foo")
    val event    = pekkocompat.ActorRefSerialize(actorRef)
    // akkacompat.ActorRefSerialize(akka://PekkoSerializationTest/user/foo#-1173772736)をkryoでシリアライズしたもの
    val serialized: Array[Byte] = Array(1,0,-39,1,106,112,46,99,111,46,116,105,115,46,108,101,114,110,97,46,112,97,121,109,101,110,116,46,97,112,112,108,105,99,97,116,105,111,110,46,101,99,112,97,121,109,101,110,116,46,105,115,115,117,105,110,103,46,97,99,116,111,114,46,97,107,107,97,99,111,109,112,97,116,46,65,99,116,111,114,82,101,102,83,101,114,105,97,108,105,122,101,1,1,1,97,107,107,97,46,97,99,116,111,114,46,82,101,112,111,105,110,116,97,98,108,101,65,99,116,111,114,82,101,-26,1,97,107,107,97,58,47,47,80,101,107,107,111,83,101,114,105,97,108,105,122,97,116,105,111,110,84,101,115,116,47,117,115,101,114,47,102,111,111,35,45,49,49,55,51,55,55,50,55,51,-74)
    println(new String(serialized, StandardCharsets.UTF_8))
    val serializer = pekkoSerializationExtension.findSerializerFor(event)
    val serializerId = serializer.identifier
    val manifest = classOf[pekkocompat.ActorRefSerialize].getName
    val deserialized = pekkoSerializationExtension.deserialize(serialized, serializerId, manifest).get
    expect(deserialized.toString === "ActorRefSerialize(Actor[pekko://PekkoSerializationTest/user/foo#-1173772736])")
  }

  object Foo {
    final case class Message(content: String)

    def props(): Props = Props(new Foo)
  }

  class Foo extends Actor with ActorLogging {

    import Foo._

    override def receive: Receive = {
      case Message(content) => log.info(content)
    }
  }
}
