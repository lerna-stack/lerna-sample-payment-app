package jp.co.tis.lerna.payment.adapter.util.shutdown

import scala.concurrent.Future

trait GracefulShutdownApplication {

  /** ShardRegionを GracefulShutdown する<br>
    * ※ 非同期に shutdown されるので、呼び出しが完了した時点で shutdown完了ではない
    */
  def requestGracefulShutdownShardRegion(): Unit

  /** ReadModelUpdaterSupervisorを Shutdown の準備させる<br>
    * ※ 非同期に shutdown の準備されるので、呼び出しが完了した時点で shutdownの準備完了ではない
    */
  def requestShutdownReadyReadModelUpdaterSupervisor(): Future[Any]
}
