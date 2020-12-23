package jp.co.tis.lerna.payment.application

/** Actorの衝突回避のための PersistenceId, ClusterSharding の typeName 用定数
  */
object ActorPrefix {

  object Ec {
    val houseMoney = "ec-house-money"
  }

}
