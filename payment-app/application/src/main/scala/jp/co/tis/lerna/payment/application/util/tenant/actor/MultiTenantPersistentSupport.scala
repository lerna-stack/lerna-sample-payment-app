package jp.co.tis.lerna.payment.application.util.tenant.actor

import akka.persistence.PersistentActor

trait MultiTenantPersistentSupport extends MultiTenantSupport { self: PersistentActor =>
  private def pluginIdPrefix =
    context.system.settings.config.getString("jp.co.tis.lerna.payment.application.persistence.plugin-id-prefix")
  // 外側でoverrideされないようにするためfinal
  final override def journalPluginId: String  = s"${pluginIdPrefix}.tenants.${tenant.id}.journal"
  final override def snapshotPluginId: String = "akka.persistence.no-snapshot-store"
}
