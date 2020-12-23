package jp.co.tis.lerna.payment.application.util.tenant.actor

import java.net.URLEncoder

import jp.co.tis.lerna.payment.application.util.tenant.MultiTenantSupportCommand
import jp.co.tis.lerna.payment.utility.tenant.AppTenant

object MultiTenantShardingSupportTestHelper {
  def generateActorName()(implicit tenant: AppTenant): String = {
    val entityId = MultiTenantShardingSupport
      .tenantSupportEntityId[DummyCommand](DummyCommand(tenant), _ => s"${generateUniqueNumber()}")

    // ClusterSharding が UTF-8 URL encode している
    URLEncoder.encode(entityId, "utf-8")
  }

  private[this] final case class DummyCommand(tenant: AppTenant) extends MultiTenantSupportCommand

  private[this] val generateUniqueNumber: () => Int = {
    val iterator = Stream.from(0).iterator
    () => iterator.next()
  }
}
