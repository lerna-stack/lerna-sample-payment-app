package jp.co.tis.lerna.payment.application.util.persistence.actor

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import akka.persistence.PersistentActor

trait ShardingPersistenceIdHelper { self: PersistentActor =>

  /** persistenceId を一意にするための機能ごとのprefix
    * 重複を回避するために Cluster Sharding の typeName のようなuniqueな値 を使用すること
    */
  def persistenceIdPrefix: String

  /** persistenceIdPrefix と entityId の区切り文字
    *
    * encode 後の文字列に含まれないため空白(" ")を使用。
    */
  private[this] def delimiter = ' '

  private[this] def encode(str: String) = {
    val encodedString = URLEncoder.encode(str, StandardCharsets.UTF_8.name)
    encodedString.ensuring(!_.contains(delimiter), s"encodedString should not contain delimiter [${delimiter}]")
  }

  protected def entityId: String = context.self.path.name

  final override def persistenceId: String = s"${encode(persistenceIdPrefix)}${delimiter}${entityId}"
}
