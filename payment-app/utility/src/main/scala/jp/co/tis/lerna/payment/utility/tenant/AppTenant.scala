package jp.co.tis.lerna.payment.utility.tenant

import jp.co.tis.lerna.payment.utility.AppRequestContext
import lerna.util.lang.Equals._

trait AppTenant extends lerna.util.tenant.Tenant

object AppTenant {
  // テナントを追加したときは人力による追加が必要
  val values: Seq[AppTenant] = Seq[AppTenant](
    Example,
    TenantA,
  )

  def withId(id: String): AppTenant = values.find(_.id === id).getOrElse {
    throw new NoSuchElementException(s"No Tenant found for '$id'")
  }

  implicit def tenant(implicit appRequestContext: AppRequestContext): AppTenant = appRequestContext.tenant
}

sealed abstract class Example extends AppTenant
case object Example extends Example {
  override def id: String = "example"
}

sealed abstract class TenantA extends AppTenant
case object TenantA extends Example {
  override def id: String = "tenant-a"
}
