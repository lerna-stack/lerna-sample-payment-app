package jp.co.tis.lerna.payment.adapter.util.health

import akka.Done
import jp.co.tis.lerna.payment.utility.tenant.AppTenant

import scala.concurrent.Future

trait HealthCheckApplication {
  def healthy()(implicit tenant: AppTenant): Future[Done]

  /** ヘルスチェックを止めて常に失敗するようにする<br>
    *   Graceful Shutdown用
    */
  def kill(): Unit
}
