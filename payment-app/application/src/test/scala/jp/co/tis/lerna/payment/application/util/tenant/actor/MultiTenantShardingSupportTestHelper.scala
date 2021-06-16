package jp.co.tis.lerna.payment.application.util.tenant.actor

import akka.cluster.sharding.ShardRegion.EntityId
import jp.co.tis.lerna.payment.utility.tenant.AppTenant

object MultiTenantShardingSupportTestHelper {
  def generateEntityId()(implicit tenant: AppTenant): EntityId = {
    MultiTenantShardingSupport.tenantSupportEntityId(s"${generateUniqueNumber().toString}")
  }

  private[this] val generateUniqueNumber: () => Int = {
    val iterator = Stream.from(0).iterator
    () => iterator.next()
  }
}
