package jp.co.tis.lerna.payment.adapter.issuing

import jp.co.tis.lerna.payment.adapter.issuing.model.{
  AcquirerReversalRequestParameter,
  AuthorizationRequestParameter,
  IssuingServiceResponse,
}
import jp.co.tis.lerna.payment.adapter.util.gateway.ExternalGateway
import jp.co.tis.lerna.payment.utility.AppRequestContext

import scala.concurrent.Future

/**  Issuing Service Gateway のIF
  */
trait IssuingServiceGateway extends ExternalGateway {

  def requestAuthorization(
      parameter: AuthorizationRequestParameter,
  )(implicit
      appRequestContext: AppRequestContext,
  ): Future[IssuingServiceResponse]

  def requestAcquirerReversal(
      parameter: AcquirerReversalRequestParameter,
      originalRequestParameter: AuthorizationRequestParameter,
  )(implicit
      appRequestContext: AppRequestContext,
  ): Future[IssuingServiceResponse]
}
