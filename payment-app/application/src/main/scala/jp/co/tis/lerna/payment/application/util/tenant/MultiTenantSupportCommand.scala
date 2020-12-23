package jp.co.tis.lerna.payment.application.util.tenant

import jp.co.tis.lerna.payment.utility.tenant.AppTenant

/** マルチテナント対応する Command が継承すべき trait
  */
trait MultiTenantSupportCommand {
  def tenant: AppTenant
}
