package jp.co.tis.lerna.payment.application.readmodelupdater.tagging

/** Event -> Tag 変換用<br>
  * EventAdapter は 1class ごとに1つしか使われないため、 Event -> Tag 変換のルールを分割して管理できるようにする
  * ドキュメント: docs/projects/application/ReadModelUpdater用タグ付け.md
  */
trait EventToTags {
  protected def mapping: PartialFunction[Any, Set[String]]

  private def default(x: Any) = Set.empty[String]

  def toTags(event: Any): Set[String] = mapping.applyOrElse[Any, Set[String]](event, default)
}
