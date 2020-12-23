package jp.co.tis.lerna.payment.application.util.persistence.actor

import akka.persistence.PersistentActor
import jp.co.tis.lerna.payment.application.util.tenant.actor.{
  MultiTenantPersistentSupport,
  MultiTenantShardingSupport,
}

trait MultiTenantShardingPersistenceIdHelper extends ShardingPersistenceIdHelper {
  self: PersistentActor with MultiTenantShardingSupport with MultiTenantPersistentSupport =>

  final override protected def entityId: String = originalEntityId
}
