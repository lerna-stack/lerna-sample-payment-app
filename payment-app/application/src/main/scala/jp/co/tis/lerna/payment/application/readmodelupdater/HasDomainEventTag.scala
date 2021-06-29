package jp.co.tis.lerna.payment.application.readmodelupdater
import scala.collection.immutable

trait HasDomainEventTag {
  def domainEventTagPrefix: String

  /** Read Model Updater を何並列で動かすか
    * <p> ※ 基本的には一度動かしたら途中で変更しない。Shard数と同じようにクラスターノードの計画された最大数の10倍程度が推奨されている。<br>
    * 参考: [[https://doc.akka.io/docs/akka-projection/current/running.html#tagging-events-in-eventsourcedbehavior Running a Projection • Akka Projection]]
    */
  def numberOfTags: Int

  lazy val domainEventTags: immutable.Seq[String] =
    Vector.tabulate(numberOfTags)(i => s"$domainEventTagPrefix-${i.toString}")
}
