package jp.co.tis.lerna.payment.application.util.tenant.actor

import com.typesafe.config.Config

trait MultiTenantPersistentSupport extends MultiTenantSupport {
  private def pluginIdPrefix(implicit config: Config) =
    config.getString("jp.co.tis.lerna.payment.application.persistence.plugin-id-prefix")
  // 外側でoverrideされないようにするためfinal
  final def journalPluginId(implicit config: Config): String = s"${pluginIdPrefix}.tenants.${tenant.id}.journal"
  final def snapshotPluginId: String                         = "akka.persistence.no-snapshot-store"
}
