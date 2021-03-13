package jp.co.tis.lerna.payment.application.readmodelupdater
import scala.collection.immutable

trait HasDomainEventTag {
  def domainEventTagPrefix: String

  /** Read Model Updater を何並列で動かすか
    */
  def numberOfTags: Int

  lazy val domainEventTags: immutable.Seq[String] = Vector.tabulate(numberOfTags)(i => s"$domainEventTagPrefix-$i")
}
