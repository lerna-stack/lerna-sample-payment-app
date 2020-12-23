package jp.co.tis.lerna.payment.presentation.util.directives

import akka.http.scaladsl.server.Directive1
import jp.co.tis.lerna.payment.utility.AppRequestContext
import lerna.http.directives.GenTraceIDDirective

trait GenAppRequestContextDirective extends GenTraceIDDirective with GenTenantDirective {
  def extractAppRequestContext: Directive1[AppRequestContext] =
    for {
      traceId <- extractTraceId
      tenant  <- extractTenantStrict
    } yield {
      AppRequestContext(traceId, tenant)
    }
}
