package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import jp.co.tis.lerna.payment.utility.scalatest.SpecAssertions
import org.scalatest.funsuite.AnyFunSuiteLike
import akka.serialization.{ SerializationExtension => AkkaSerializationExtension }
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit => PekkoScalaTestWithActorTestKit }
import pekko.serialization.{ SerializationExtension => PekkoSerializationExtension }

import java.nio.charset.StandardCharsets

class KryoSerializationCompatibilityTest

class AkkaSerializationTest extends ScalaTestWithActorTestKit() with SpecAssertions with AnyFunSuiteLike {

  import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }

  private val akkaSerializationExtension = AkkaSerializationExtension(system)

  test("Serialize ActorRef in Akka") {
    val s        = ActorSystem("foobazbar")
    val actorRef = s.actorOf(Foo.props(), "foo")
    val event    = AkkaActorRef(actorRef)
    println(event)
    val bytes = akkaSerializationExtension.serialize(event).get
    println(bytes.mkString(","))
    val serialized = new String(bytes, StandardCharsets.UTF_8)
    println(serialized)

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
//    val s = ActorSystem("foobazbar")
//    val actorRef = s.actorOf(Foo.props(), "foo")
//    val event = PekkoActorRef(event)
//    println(event)
    val serialized: Array[Byte] = Array(1,0,-55,1,106,112,46,99,111,46,116,105,115,46,108,101,114,110,97,46,112,97,121,109,101,110,116,46,97,112,112,108,105,99,97,116,105,111,110,46,101,99,112,97,121,109,101,110,116,46,105,115,115,117,105,110,103,46,97,99,116,111,114,46,65,107,107,97,65,99,116,111,114,82,101,102,1,1,1,97,107,107,97,46,97,99,116,111,114,46,82,101,112,111,105,110,116,97,98,108,101,65,99,116,111,114,82,101,-26,1,97,107,107,97,58,47,47,102,111,111,98,97,122,98,97,114,47,117,115,101,114,47,102,111,111,35,49,56,56,48,55,57,56,56,57,-80)
    val deserialized = pekkoSerializationExtension.deserialize(serialized, 9001, classOf[PekkoActorRef].getName).get
    println(deserialized)
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
