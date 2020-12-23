# マルチテナント化されたClusterShardingの実装ガイド

## ClusterSharding の Entity Actor の実装にあたって必要なこと
1. Actor class に 以下の 3 class を継承する
    - `jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantPersistentSupport`
    - `jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantShardingSupport`
    - `jp.co.tis.lerna.payment.application.util.persistence.actor.MultiTenantShardingPersistenceIdHelper`
1. `def tenantSupportEntityId` を `extractEntityId` 定義の際に使用する


## 実装例

### Actor
`application.util.tenant.actor` の 3 class を継承する。

```scala
import akka.persistence.PersistentActor
import jp.co.tis.lerna.payment.application.ActorPrefix
import jp.co.tis.lerna.payment.application.util.persistence.actor.MultiTenantShardingPersistenceIdHelper
import jp.co.tis.lerna.payment.application.util.tenant.actor._
import lerna.log.AppActorLogging

class PaymentActor(
    /* 略 */
) extends PersistentActor
    with MultiTenantPersistentSupport
    with MultiTenantShardingSupport
    with MultiTenantShardingPersistenceIdHelper
    with AppActorLogging {

  override def persistenceIdPrefix: String = ActorPrefix.Ec.houseMoney
  /* 略 */
}
````

### `extractEntityId`
`MultiTenantShardingSupport.tenantSupportEntityId` を使用する。

```scala
import akka.cluster.sharding.ShardRegion.EntityId
import akka.cluster.sharding.ShardRegion
import jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantShardingSupport 

object PaymentActor {
  def calculateEntityId(command: Command): EntityId =
    s"${command.clientId.value}-${command.walletShopId.value}-${command.orderId.value}"

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case request @ AtLeastOnceDeliveryRequest(command: Command) =>
      val entityId = MultiTenantShardingSupport.tenantSupportEntityId(command, calculateEntityId)
      (entityId, request)
  }
}
```


## それぞれのクラスの役割（参考情報）
- [jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantPersistentSupport](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/util/tenant/actor/MultiTenantPersistentSupport.scala)
    - テナント別に接続先 Cassandra を切り替えるために必要
- [jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantShardingSupport](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/util/tenant/actor/MultiTenantShardingSupport.scala)
    - Actor 側でテナントを特定するために必要
        - ※ ShardRegion は 全テナントで共有している
        - Actor name(`entityId`) に テナント と エンティティID を入れて `MultiTenantShardingSupport` で取り出している
    - `def tenantSupportEntityId` を `extractEntityId` 定義の際に使用する
        - 対象 `Command` には [jp.co.tis.lerna.payment.application.util.tenant.MultiTenantSupportCommand](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/util/tenant/MultiTenantSupportCommand.scala) の継承が必要
- [jp.co.tis.lerna.payment.application.util.persistence.actor.MultiTenantShardingPersistenceIdHelper](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/util/persistence/actor/MultiTenantShardingPersistenceIdHelper.scala)
    - `Sharding` と `PersistentActor` を使う場合に、 `persistenceId` の重複が発生しないようにすために利用すると良い
    - `ShardingPersistenceIdHelper` のマルチテナント対応版
        - `MultiTenantShardingSupport` の `originalEntityId` を使用して `entityId` を override している 
