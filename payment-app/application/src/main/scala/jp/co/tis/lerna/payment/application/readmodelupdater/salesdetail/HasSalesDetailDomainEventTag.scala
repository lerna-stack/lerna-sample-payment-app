package jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail

import jp.co.tis.lerna.payment.application.readmodelupdater.HasDomainEventTag

trait HasSalesDetailDomainEventTag extends HasDomainEventTag {
  val componentName: String = "SalesDetail"
  def categoryName: String
  override def domainEventTagPrefix: String = categoryName + componentName
}
