package jp.co.tis.lerna.payment.gateway.mock

import lerna.testkit.airframe.DISessionSupport
import org.scalatest.{ BeforeAndAfterEach, TestSuite }

@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
trait IssuingServiceMockSupport extends BeforeAndAfterEach {
  this: TestSuite with DISessionSupport =>

  protected lazy val serviceMock: IssuingServiceMock = diSession.build[IssuingServiceMock]

  override def afterEach(): Unit = {
    serviceMock.server.resetAll()
    super.afterEach()
  }
}
