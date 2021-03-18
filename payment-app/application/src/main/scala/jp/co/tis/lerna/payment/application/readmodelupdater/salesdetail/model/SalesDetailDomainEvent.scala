package jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.model

import lerna.util.trace.TraceId

/** 継承すると [[jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.SalesDetailEventHandler]] で処理されるようになる
  * ※ [[jp.co.tis.lerna.payment.application.readmodelupdater.tagging.TaggingEventAdapter]] でタグが付与される
  */
trait SalesDetailDomainEvent {
  def traceId: TraceId
}

object SalesDetailDomainEvent {
  val tag = "SalesDetail"
}

/** 同一PersistenceIdから1つの結果（レコード）しか生成されない取引<br>
  * persistenceId のみで結果の重複チェックを行なう<br>
  * 【例】
  * <ul>
  *   <li>冪等な通知</li>
  * </ul>
  */
trait SingleResultTransaction extends SalesDetailDomainEvent

/** 同一PersistenceIdから複数の結果（レコード）が生成される取引<br>
  * persistenceId と sequenceNr で結果の重複チェックを行なう<br>
  * 【例】
  * <ul>
  *   <li>Actorが使い回されるAPI</li>
  *   <li>決済・取消が存在するAPI</li>
  * </ul>
  */
trait MultipleResultTransaction extends SalesDetailDomainEvent
