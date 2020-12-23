package jp.co.tis.lerna.payment.application.util.tenant.actor

import jp.co.tis.lerna.payment.application.util.tenant.MultiTenantSupportCommand
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec
import jp.co.tis.lerna.payment.utility.tenant.{ AppTenant, Example }
import org.scalatest.BeforeAndAfterAll

object MultiTenantShardingSupportSpec {
  private final case class DummyCommand(tenant: AppTenant) extends MultiTenantSupportCommand
}

class MultiTenantShardingSupportSpec extends StandardSpec with BeforeAndAfterAll {

  import MultiTenantShardingSupportSpec._
  import jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantShardingSupport.{
    delimiter,
    extractTenantAndEntityId,
    tenantSupportEntityId,
  }

  "tenantSupportEntityId() & extractTenantAndEntityId()" when {
    "すべてのテナントで" should {
      "tenant を抽出できる" in {
        val originalEntityId = "dummy"
        AppTenant.values.foreach { tenant =>
          val entityId = tenantSupportEntityId[DummyCommand](DummyCommand(tenant), _ => originalEntityId)
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
        val originalEntityId = s"dummy${delimiter}aaa${delimiter}bbb"
        val tenant           = Example
        val entityId         = tenantSupportEntityId[DummyCommand](DummyCommand(tenant), _ => originalEntityId)
        val result           = extractTenantAndEntityId(entityId)
        expect {
          result._1 === tenant
          result._2 === originalEntityId
        }
      }
    }
  }
}
