package jp.co.tis.lerna.payment.application.util.tenant.actor

import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.{ AppTenant, Example }
import org.scalatest.BeforeAndAfterAll

class MultiTenantShardingSupportSpec extends StandardSpec with BeforeAndAfterAll {

  import jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantShardingSupport.{
    extractTenantAndEntityId,
    tenantSupportEntityId,
  }

  private val delimiter = MultiTenantShardingSupport.delimiter.toString

  "tenantSupportEntityId() & extractTenantAndEntityId()" when {
    "すべてのテナントで" should {
      "tenant を抽出できる" in {
        val originalEntityId = "dummy"
        AppTenant.values.foreach { implicit tenant =>
          val entityId = tenantSupportEntityId(originalEntityId)
          val result   = extractTenantAndEntityId(entityId)
          expect {
            result._1 === tenant
            result._2 === originalEntityId
          }
        }
      }
    }

    s"""originalEntityId に delimiter("$delimiter") が含まれていても""" should {
      "originalEntityId を抽出できる" in {
        val originalEntityId           = s"dummy${delimiter}aaa${delimiter}bbb"
        implicit val tenant: AppTenant = Example
        val entityId                   = tenantSupportEntityId(originalEntityId)
        val result                     = extractTenantAndEntityId(entityId)
        expect {
          result._1 === tenant
          result._2 === originalEntityId
        }
      }
    }
  }
}
