package jp.co.tis.lerna.payment.presentation.util.directives

import akka.http.scaladsl.server.Directive1
import jp.co.tis.lerna.payment.utility.AppRequestContext
import lerna.http.directives.GenTraceIDDirective
import lerna.log.{ LogContext, SystemComponentLogContext }

private[util] trait GenLogContextDirective extends GenTraceIDDirective with GenTenantDirective {

  private[util] def extractLogContext: Directive1[LogContext] = {
    for {
      traceId     <- extractTraceId
      maybeTenant <- extractTenantOption
    } yield {
      maybeTenant match {
        case Some(tenant) => AppRequestContext(traceId, tenant)
        case None         => SystemComponentLogContext
      }
    }
  }
}
