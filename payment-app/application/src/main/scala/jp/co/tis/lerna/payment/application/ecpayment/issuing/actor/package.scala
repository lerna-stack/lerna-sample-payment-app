package jp.co.tis.lerna.payment.application.ecpayment.issuing

import jp.co.tis.lerna.payment.utility.AppRequestContext
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.util.tenant.Tenant
import lerna.util.trace.{ RequestContext, TraceId }

package object actor {
  private[actor] implicit def contextToTraceId(implicit appRequestContext: AppRequestContext): TraceId =
    appRequestContext.traceId

  private[actor] implicit def traceIdToRequestContext(implicit _traceId: TraceId, _tenant: AppTenant): RequestContext =
    new RequestContext {
      override def traceId: TraceId = _traceId
      override def tenant: Tenant   = _tenant
    }
}
