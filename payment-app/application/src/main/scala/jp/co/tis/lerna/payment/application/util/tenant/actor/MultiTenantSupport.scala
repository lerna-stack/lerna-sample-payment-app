package jp.co.tis.lerna.payment.application.util.tenant.actor

import jp.co.tis.lerna.payment.utility.tenant.AppTenant

/** マルチテナント対応するActorが継承すべき trait
  * 今の所マルチテナントを class レベルで認識するのは Actor のみであるため Actor 限定としている
  */
trait MultiTenantSupport {
  implicit def tenant: AppTenant
}
