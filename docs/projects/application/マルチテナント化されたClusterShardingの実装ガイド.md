# マルチテナント化されたClusterShardingの実装ガイド

## ClusterSharding の Entity Actor の実装にあたって必要なこと
1. Actor or Setup class に 以下の 2 class を継承する
    - `jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantPersistentSupport`
    - `jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantShardingSupport`
1. `def tenantSupportEntityId` を `extractEntityId` 定義の際に使用する


## 実装例

### Actor
Actor or Setup class に `application.util.tenant.actor` の 2 class を継承する。

#### Actor のケース
```scala
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.application.util.tenant.actor._

class PaymentActor(
                          /* 略 */
                          config: Config,
                          val entityContext: EntityContext[Command],
                  ) extends MultiTenantPersistentSupport
        with MultiTenantShardingSupport[Command] {

    def eventSourcedBehavior(): EventSourcedBehavior[Command, Event, State] = {
        val persistenceId = PersistenceId.of(entityContext.entityTypeKey.name, originalEntityId)
        EventSourcedBehavior[Command, ECPaymentIssuingServiceEvent, State](
            persistenceId = persistenceId,
            emptyState = ???,
            commandHandler = (state, command) => state.applyCommand(command),
            eventHandler = (state, event) => state.applyEvent(event),
        )
                .withJournalPluginId(journalPluginId(config))
                .withSnapshotPluginId(snapshotPluginId)
    }
}
```

#### Setup のケース
```scala
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.application.util.tenant.actor._

private[actor] final case class Setup(
    /* 略 */
    entityContext: EntityContext[Command],
) extends MultiTenantShardingSupport[Command]

class PaymentActor(
    /* 略 */
    config: Config,
    setup: Setup,    
) extends MultiTenantPersistentSupport {
  
    override implicit def tenant: AppTenant = setup.tenant

    def eventSourcedBehavior(): EventSourcedBehavior[Command, Event, State] = {
        val persistenceId = PersistenceId.of(setup.entityContext.entityTypeKey.name, setup.originalEntityId)
        EventSourcedBehavior[Command, ECPaymentIssuingServiceEvent, State](
            persistenceId = persistenceId,
            emptyState = ???,
            commandHandler = (state, command) => state.applyCommand(command),
            eventHandler = (state, event) => state.applyEvent(event),
        )
          .withJournalPluginId(journalPluginId(config))
          .withSnapshotPluginId(snapshotPluginId)
    }
}
```

### `EntityId`
`MultiTenantShardingSupport.tenantSupportEntityId` を使用する。

```scala
import akka.cluster.sharding.ShardRegion.EntityId
import akka.cluster.sharding.typed.ShardingEnvelope
import jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantShardingSupport
import jp.co.tis.lerna.payment.utility.tenant.AppTenant

sealed trait Command

val entityId: EntityId = ???
val command: Command = ???
implicit val tenant: AppTenant = ???

val tenantSupportEntityId = MultiTenantShardingSupport.tenantSupportEntityId(entityId)
ShardingEnvelope[Command](tenantSupportEntityId, command)
```


## それぞれのクラスの役割（参考情報）
- [jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantPersistentSupport](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/util/tenant/actor/MultiTenantPersistentSupport.scala)
    - テナント別に接続先 Cassandra を切り替えるために必要
- [jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantShardingSupport](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/util/tenant/actor/MultiTenantShardingSupport.scala)
    - Actor 側でテナントを特定するために必要
        - ※ ShardRegion は 全テナントで共有している
        - ClusterSharding の `entityId` に テナント と エンティティID を入れて `MultiTenantShardingSupport` で取り出している
    - `def tenantSupportEntityId` を `ShardingEnvelope` 作成の際に使用する 
