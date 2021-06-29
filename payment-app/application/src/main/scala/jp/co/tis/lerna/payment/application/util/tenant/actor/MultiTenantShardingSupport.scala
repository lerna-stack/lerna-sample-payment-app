package jp.co.tis.lerna.payment.application.util.tenant.actor

import akka.cluster.sharding.ShardRegion.EntityId
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.akka.entityreplication.typed.ReplicatedEntityContext
import lerna.util.lang.Equals._

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets

object MultiTenantShardingSupport {

  /** 文字列化された tenantId と originalEntityId の区切り文字
    *
    * encode 後の文字列に含まれないため空白(" ")を使用。
    * 空白は (ClusterShardingによる) UTF-8 URLEncode で "+" になるので actor path を見たときに理解しやすい。
    */
  private[tenant] val delimiter = ' '

  private[this] def encode(str: String) = {
    val encodedString = URLEncoder.encode(str, StandardCharsets.UTF_8.name)
    encodedString.ensuring(
      !_.contains(delimiter),
      s"encodedString should not contain delimiter [${delimiter.toString}]",
    )
  }
  private[this] def decode(str: String) = URLDecoder.decode(str, StandardCharsets.UTF_8.name)

  def tenantSupportEntityId(entityId: EntityId)(implicit tenant: AppTenant): String = {
    val encodedTenantId         = encode(tenant.id)
    val encodedOriginalEntityId = encode(entityId)

    s"${encodedTenantId}${delimiter.toString}${encodedOriginalEntityId}"
  }

  private[tenant] def extractTenantAndEntityId(entityId: String): (AppTenant, String) = {
    require(
      entityId.count(_ === delimiter) === 1,
      s"The entityId must be able to be split in exactly 2 with the delimiter [${delimiter.toString}]",
    )

    val (encodedTenantId, encodedOriginalEntityId) = (entityId.split(delimiter): @unchecked) match {
      case Array(_1, _2) => (_1, _2) // `match may not be exhaustive.` 警告対策
    }

    val tenant           = AppTenant.withId(decode(encodedTenantId))
    val originalEntityId = decode(encodedOriginalEntityId)

    (tenant, originalEntityId)
  }
}

/** ClusterSharding で　Entity Actor として使用されるマルチテナント対応 Actor用
  */
trait MultiTenantShardingSupport[Command] extends MultiTenantSupport {
  def entityContext: ReplicatedEntityContext[Command]

  private[this] val (_tenant, _originalEntityId) =
    MultiTenantShardingSupport.extractTenantAndEntityId(entityContext.entityId)

  override implicit def tenant: AppTenant = _tenant

  def originalEntityId: String = _originalEntityId
}
