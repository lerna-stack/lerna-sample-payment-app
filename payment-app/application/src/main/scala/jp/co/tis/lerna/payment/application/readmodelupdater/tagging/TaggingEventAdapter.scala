package jp.co.tis.lerna.payment.application.readmodelupdater.tagging

import akka.actor.ExtendedActorSystem
import akka.persistence.journal.{ Tagged, WriteEventAdapter }
import jp.co.tis.lerna.payment.application.readmodelupdater.tagging.salesdetail.SalesDetailEventToTags
import jp.co.tis.lerna.payment.application.readmodelupdater.tagging.salesdetail.SalesDetailEventToTags

class TaggingEventAdapter(system: ExtendedActorSystem) extends WriteEventAdapter {
  override def manifest(event: Any): String = "" // when no manifest needed, return ""

  override def toJournal(event: Any): Any = {
    // EventAdapter は 1class ごとに1つしか使われないため、 Event -> Tag 変換のルールを分割して管理できるようにする
    // ドキュメント: docs/projects/application/ReadModelUpdater用タグ付け.md
    val eventToTagsSet = Set[EventToTags](
      SalesDetailEventToTags,
    )

    val tags = eventToTagsSet.flatMap(_.toTags(event))

    if (tags.isEmpty) event
    else Tagged(event, tags)
  }
}
