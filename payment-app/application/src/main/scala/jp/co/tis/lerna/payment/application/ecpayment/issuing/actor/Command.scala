package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import akka.cluster.sharding.ShardRegion.EntityId
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.AmountTran
import jp.co.tis.lerna.payment.adapter.ecpayment.model.{ OrderId, WalletShopId }
import jp.co.tis.lerna.payment.adapter.issuing.model.{
  AcquirerReversalRequestParameter,
  AuthorizationRequestParameter,
  IssuingServiceResponse,
}
import jp.co.tis.lerna.payment.adapter.util.OnlineProcessingFailureMessage
import jp.co.tis.lerna.payment.adapter.wallet.{ ClientId, CustomerId }
import jp.co.tis.lerna.payment.application.ecpayment.issuing.IssuingServicePayCredential
import jp.co.tis.lerna.payment.application.util.tenant.MultiTenantSupportCommand
import jp.co.tis.lerna.payment.utility.AppRequestContext
import jp.co.tis.lerna.payment.utility.tenant.AppTenant

sealed trait Command

case object StopActor extends Command

sealed trait BusinessCommand extends Command with MultiTenantSupportCommand {
  def clientId: ClientId
  def walletShopId: WalletShopId
  def orderId: OrderId
  def entityId: EntityId = s"${clientId.value}-${walletShopId.value}-${orderId.value}"
  def appRequestContext: AppRequestContext
  override def tenant: AppTenant = appRequestContext.tenant
}

final case class Settle(
    clientId: ClientId,
    customerId: CustomerId,
    walletShopId: WalletShopId,
    orderId: OrderId,
    amountTran: AmountTran,
)(implicit val appRequestContext: AppRequestContext)
    extends BusinessCommand

final case class Cancel(
    clientId: ClientId,
    customerId: CustomerId,
    walletShopId: WalletShopId,
    orderId: OrderId,
)(implicit val appRequestContext: AppRequestContext)
    extends BusinessCommand

private[actor] trait InnerBusinessCommand extends BusinessCommand {
  implicit def processingContext: ProcessingContext

  implicit override def appRequestContext: AppRequestContext = processingContext.appRequestContext

  override def clientId: ClientId         = processingContext.clientId
  override def walletShopId: WalletShopId = processingContext.walletShopId
  override def orderId: OrderId           = processingContext.orderId
}

final case class SettlementResult(
    result: Either[
      OnlineProcessingFailureMessage,
      (
          IssuingServicePayCredential,
          AuthorizationRequestParameter,
          Either[OnlineProcessingFailureMessage, IssuingServiceResponse],
      ),
    ],
)(implicit
    val processingContext: ProcessingContext,
) extends InnerBusinessCommand

final case class CancelResult(
    result: Either[
      OnlineProcessingFailureMessage,
      (
          Either[OnlineProcessingFailureMessage, IssuingServiceResponse],
          IssuingServicePayCredential,
          AcquirerReversalRequestParameter,
      ),
    ],
)(implicit
    val processingContext: ProcessingContext,
) extends InnerBusinessCommand
