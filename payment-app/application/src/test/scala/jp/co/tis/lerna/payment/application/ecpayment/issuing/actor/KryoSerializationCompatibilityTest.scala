package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import jp.co.tis.lerna.payment.utility.scalatest.SpecAssertions
import org.scalatest.funsuite.AnyFunSuiteLike
import akka.serialization.{SerializationExtension => AkkaSerializationExtension}
import jp.co.tis.lerna.payment.application.ecpayment.issuing.actor
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit => PekkoScalaTestWithActorTestKit}
import pekko.serialization.{SerializationExtension => PekkoSerializationExtension}

import java.nio.charset.StandardCharsets

class KryoSerializationCompatibilityTest

class AkkaSerializationTest extends ScalaTestWithActorTestKit() with SpecAssertions with AnyFunSuiteLike {

  import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
  import akka.actor.typed.scaladsl.adapter.ClassicActorRefOps
  import akka.util.ByteString

  private val akkaSerializationExtension = AkkaSerializationExtension(system)
  private val actorSystem        = ActorSystem("PekkoSerializationTest")
  private val actorRef = actorSystem.actorOf(Foo.props(), "foo")

//  test("Serialize ActorRef in Akka") {
//    val event    = ActorRefSerialize(actorRef)
//    val bytes = akkaSerializationExtension.serialize(event).get
//    println(bytes.mkString(","))
//    println(new String(bytes, StandardCharsets.UTF_8))
//    val serializerId = akkaSerializationExtension.findSerializerFor(event).identifier
//    val manifest = classOf[ActorRefSerialize].getName
//    val deserialized = akkaSerializationExtension.deserialize(bytes, serializerId, manifest).get
//    println(deserialized)
//    expect(event === deserialized)
//  }
//
//  test("Serialize typed ActorRef in Akka") {
//    val event = TypedActorRefSerialize(actorRef.toTyped)
//    val bytes = akkaSerializationExtension.serialize(event).get
//    println(bytes.mkString(","))
//    println(new String(bytes, StandardCharsets.UTF_8))
//    val serializerId = akkaSerializationExtension.findSerializerFor(event).identifier
//    val manifest = classOf[TypedActorRefSerialize[Nothing]].getName
//    val deserialized = akkaSerializationExtension.deserialize(bytes, serializerId, manifest).get
//    println(deserialized)
//    expect(event === deserialized)
//  }
//
//  test("Serialize ByteString in Akka") {
//    val event = ByteStringSerialize(ByteString("foo"))
//    val bytes = akkaSerializationExtension.serialize(event).get
//    println(bytes.mkString(","))
//    println(new String(bytes, StandardCharsets.UTF_8))
//    val serializerId = akkaSerializationExtension.findSerializerFor(event).identifier
//    val manifest = classOf[ByteStringSerialize].getName
//    val deserialized = akkaSerializationExtension.deserialize(bytes, serializerId, manifest).get
//    println(deserialized)
//    expect(event === deserialized)
//  }

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
  import pekko.actor.typed.scaladsl.adapter.ClassicActorRefOps
  import pekko.util.ByteString

  private val pekkoSerializationExtension = PekkoSerializationExtension(system)

  private val actorSystem        = ActorSystem("PekkoSerializationTest")
  private val actorRef = actorSystem.actorOf(Foo.props(), "foo")

  test("Deserialize ActorRef in Pekko") {
    val event    = ActorRefSerialize(actorRef)
    // ActorRefSerialize(akka://PekkoSerializationTest/user/foo#2002623825)をkryoでシリアライズしたもの
    val serialized: Array[Byte] = Array(1,0,-50,1,106,112,46,99,111,46,116,105,115,46,108,101,114,110,97,46,112,97,121,109,101,110,116,46,97,112,112,108,105,99,97,116,105,111,110,46,101,99,112,97,121,109,101,110,116,46,105,115,115,117,105,110,103,46,97,99,116,111,114,46,65,99,116,111,114,82,101,102,83,101,114,105,97,108,105,122,101,1,1,1,97,107,107,97,46,97,99,116,111,114,46,82,101,112,111,105,110,116,97,98,108,101,65,99,116,111,114,82,101,-26,1,97,107,107,97,58,47,47,80,101,107,107,111,83,101,114,105,97,108,105,122,97,116,105,111,110,84,101,115,116,47,117,115,101,114,47,102,111,111,35,50,48,48,50,54,50,51,56,50,-75)
    println(new String(serialized, StandardCharsets.UTF_8))
    val serializer = pekkoSerializationExtension.findSerializerFor(event)
    val serializerId = serializer.identifier
    val manifest = classOf[ActorRefSerialize].getName
    val deserialized = pekkoSerializationExtension.deserialize(serialized, serializerId, manifest).get
    expect(deserialized.toString === "ActorRefSerialize(Actor[pekko://PekkoSerializationTest/user/foo#2002623825])")
  }

  test("Deserialize typed ActorRef in Pekko") {
    val event    = TypedActorRefSerialize(actorRef.toTyped)
    val serialized: Array[Byte] = Array(1,0,-45,1,106,112,46,99,111,46,116,105,115,46,108,101,114,110,97,46,112,97,121,109,101,110,116,46,97,112,112,108,105,99,97,116,105,111,110,46,101,99,112,97,121,109,101,110,116,46,105,115,115,117,105,110,103,46,97,99,116,111,114,46,84,121,112,101,100,65,99,116,111,114,82,101,102,83,101,114,105,97,108,105,122,101,1,1,1,97,107,107,97,46,97,99,116,111,114,46,116,121,112,101,100,46,105,110,116,101,114,110,97,108,46,97,100,97,112,116,101,114,46,65,99,116,111,114,82,101,102,65,100,97,112,116,101,-14,1,1,2,97,107,107,97,46,97,99,116,111,114,46,82,101,112,111,105,110,116,97,98,108,101,65,99,116,111,114,82,101,-26,1,97,107,107,97,58,47,47,80,101,107,107,111,83,101,114,105,97,108,105,122,97,116,105,111,110,84,101,115,116,47,117,115,101,114,47,102,111,111,35,51,56,51,48,50,52,52,56,-76)
//    val serialized = pekkoSerializationExtension.serialize(event).get
    println(new String(serialized, StandardCharsets.UTF_8))
    val serializer = pekkoSerializationExtension.findSerializerFor(event)
    val serializerId = serializer.identifier
    println(serializerId)
    val manifest = classOf[TypedActorRefSerialize[Nothing]].getName
    val deserialized = pekkoSerializationExtension.deserialize(serialized, serializerId, manifest).get
    println(deserialized)
//    expect(deserialized.toString === "TypedActorRefSerialize(Actor[pekko://PekkoSerializationTest/user/foo#383024484])")
  }

  test("Deserialize ByteString in Pekko") {
    val event = ByteStringSerialize(ByteString(""))
    val serializer = pekkoSerializationExtension.findSerializerFor(event)
    val serializerId = serializer.identifier
    println(serializerId)

    // akkacompat.ByteStringSerialize("foo") をakkaでシリアライズしたバイト列
    val serialized: Array[Byte] = Array(1,0,-48,1,106,112,46,99,111,46,116,105,115,46,108,101,114,110,97,46,112,97,121,109,101,110,116,46,97,112,112,108,105,99,97,116,105,111,110,46,101,99,112,97,121,109,101,110,116,46,105,115,115,117,105,110,103,46,97,99,116,111,114,46,66,121,116,101,83,116,114,105,110,103,83,101,114,105,97,108,105,122,101,1,1,1,97,107,107,97,46,117,116,105,108,46,66,121,116,101,83,116,114,105,110,103,36,66,121,116,101,83,116,114,105,110,103,49,-61,1,3,102,111,111)

    val manifest = classOf[ByteStringSerialize].getName
    val deserialized = pekkoSerializationExtension.deserialize(serialized, serializerId, manifest).get
    println(deserialized)
    expect(deserialized === actor.ByteStringSerialize(ByteString("foo")))
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
