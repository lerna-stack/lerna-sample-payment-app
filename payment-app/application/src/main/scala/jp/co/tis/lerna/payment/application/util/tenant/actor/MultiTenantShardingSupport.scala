package jp.co.tis.lerna.payment.application.util.tenant.actor

import java.net.{ URLDecoder, URLEncoder }
import java.nio.charset.StandardCharsets

import akka.actor.Actor
import akka.cluster.sharding.ShardRegion
import jp.co.tis.lerna.payment.application.util.tenant.MultiTenantSupportCommand
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.util.lang.Equals._

object MultiTenantShardingSupport {

  /** 文字列化された tenantId と originalEntityId の区切り文字
    *
    * encode 後の文字列に含まれないため空白(" ")を使用。
    * 空白は (ClusterShardingによる) UTF-8 URLEncode で "+" になるので actor path を見たときに理解しやすい。
    */
  private[tenant] val delimiter = ' '

  private[this] def encode(str: String) = {
    val encodedString = URLEncoder.encode(str, StandardCharsets.UTF_8.name)
    encodedString.ensuring(!_.contains(delimiter), s"encodedString should not contain delimiter [${delimiter}]")
  }
  private[this] def decode(str: String) = URLDecoder.decode(str, StandardCharsets.UTF_8.name)

  def tenantSupportEntityId[Command <: MultiTenantSupportCommand](
      command: Command,
      calculateEntityId: Command => ShardRegion.EntityId,
  ): String = {
    val encodedTenantId         = encode(command.tenant.id)
    val encodedOriginalEntityId = encode(calculateEntityId(command))

    s"${encodedTenantId}${delimiter}${encodedOriginalEntityId}"
  }

  private[tenant] def extractTenantAndEntityId(entityId: String): (AppTenant, String) = {
    require(
      entityId.count(_ === delimiter) === 1,
      s"The entityId must be able to be split in exactly 2 with the delimiter [${delimiter}]",
    )

    val Array(encodedTenantId, encodedOriginalEntityId) = entityId.split(delimiter)

    val tenant           = AppTenant.withId(decode(encodedTenantId))
    val originalEntityId = decode(encodedOriginalEntityId)

    (tenant, originalEntityId)
  }
}

/** ClusterSharding で　Entity Actor として使用されるマルチテナント対応 Actor
  */
trait MultiTenantShardingSupport extends MultiTenantSupport { self: Actor =>
  // ClusterSharding が entityId を actor name にする際 URLEncode しているため元に戻す (Akka の仕様変更 or typed 化したときに注意)
  private[this] def rawEntityId = URLDecoder.decode(context.self.path.name, StandardCharsets.UTF_8.name)
  // 初期化順の関係で `val` だと 初期化中に MultiTenantPersistentActor から参照されて null になるため `lazy val` とする
  private[this] lazy val (_tenant, _originalEntityId) =
    MultiTenantShardingSupport.extractTenantAndEntityId(rawEntityId)

  override implicit def tenant: AppTenant = _tenant

  def originalEntityId: String = _originalEntityId
}
