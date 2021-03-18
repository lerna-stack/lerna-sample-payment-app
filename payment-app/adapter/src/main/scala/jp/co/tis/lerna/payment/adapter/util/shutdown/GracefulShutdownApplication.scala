package jp.co.tis.lerna.payment.adapter.util.shutdown

trait GracefulShutdownApplication {

  /** ShardRegionを GracefulShutdown する<br>
    * ※ 非同期に shutdown されるので、呼び出しが完了した時点で shutdown完了ではない
    */
  def requestGracefulShutdownShardRegion(): Unit
}
