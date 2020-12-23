package jp.co.tis.lerna.payment.application.util.application

import akka.actor.ActorSystem
import akka.testkit.TestKit
import jp.co.tis.lerna.payment.adapter.util.authorization.model.{ AuthorizationScope, Subject }
import jp.co.tis.lerna.payment.adapter.wallet.ClientId
import jp.co.tis.lerna.payment.application.ApplicationDIDesign
import jp.co.tis.lerna.payment.application.util.authorization.ClientAuthorizationApplicationImpl
import jp.co.tis.lerna.payment.utility.AppRequestContext
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.Example
import lerna.testkit.airframe.DISessionSupport
import lerna.util.trace.TraceId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import wvlet.airframe.Design

@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
  ),
)
final class ClientAuthorizationApplicationImplSpec
    extends TestKit(ActorSystem("ClientAuthorizationApplicationImplSpec"))
    with StandardSpec
    with BeforeAndAfterAll
    with ScalaFutures
    with DISessionSupport {

  override protected val diDesign: Design = ApplicationDIDesign.applicationDesign

  val auth: ClientAuthorizationApplicationImpl = diSession.build[ClientAuthorizationApplicationImpl]

  "ClientAuthorizationApplicationImpl.authorize" should {
    "always return an authorization success result" in {
      implicit val context: AppRequestContext = AppRequestContext(TraceId("1"), tenant = Example)
      val scopes                              = Seq(AuthorizationScope("scope-1"), AuthorizationScope("scope-2"))
      val res                                 = auth.authorize("token", scopes).futureValue

      assert(res.subject === Subject("dummy"))
      assert(res.clientId === ClientId(0))
    }
  }

}
