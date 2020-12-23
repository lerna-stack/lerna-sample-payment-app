package jp.co.tis.lerna.payment.gateway.mock

import lerna.testkit.airframe.DISessionSupport
import org.scalatest.{ BeforeAndAfterEach, TestSuite }

@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
trait NotificationSystemMockSupport extends BeforeAndAfterEach {
  this: TestSuite with DISessionSupport =>

  protected lazy val notificationSystemMock: NotificationSystemMock = diSession.build[NotificationSystemMock]

  override def afterEach(): Unit = {
    notificationSystemMock.server.resetAll()
    super.afterEach()
  }
}
