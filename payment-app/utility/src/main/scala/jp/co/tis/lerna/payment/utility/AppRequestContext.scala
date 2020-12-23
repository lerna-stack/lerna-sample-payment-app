package jp.co.tis.lerna.payment.utility

import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.util.trace.TraceId

final case class AppRequestContext(traceId: TraceId, tenant: AppTenant) extends lerna.util.trace.RequestContext
