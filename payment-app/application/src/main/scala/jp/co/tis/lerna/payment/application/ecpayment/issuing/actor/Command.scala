package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import akka.actor.typed.ActorRef
import akka.cluster.sharding.ShardRegion.EntityId
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.{ AmountTran, SettlementResponse }
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
import lerna.util.akka.AtLeastOnceDelivery
import lerna.util.time.LocalDateTimeFactory
import lerna.util.trace.RequestContext

import java.time.LocalDateTime
import scala.concurrent.duration.FiniteDuration

sealed trait Command

case object StopActor extends Command

case object ReceiveTimeout extends Command

final case class ProcessingTimeout(timeout: lerna.util.akka.ProcessingTimeout) extends Command {
  def timeLeft(implicit dateTimeFactory: LocalDateTimeFactory): FiniteDuration = timeout.timeLeft
  implicit def requestContext: RequestContext                                  = timeout.requestContext
}

object ProcessingTimeout {
  def apply(acceptedDateTime: LocalDateTime, askTimeout: FiniteDuration, config: Config)(implicit
      requestContext: RequestContext,
  ): ProcessingTimeout = apply(lerna.util.akka.ProcessingTimeout(acceptedDateTime, askTimeout, config))
}

sealed trait BusinessCommand extends Command with MultiTenantSupportCommand {
  def clientId: ClientId
  def walletShopId: WalletShopId
  def orderId: OrderId
  def entityId: EntityId = s"${clientId.value}-${walletShopId.value}-${orderId.value}"
  def appRequestContext: AppRequestContext
  override def tenant: AppTenant = appRequestContext.tenant
}

trait AtLeastOnceDeliveryAcceptable {
  def confirmTo: ActorRef[AtLeastOnceDelivery.Confirm]
  def accept(): Unit = confirmTo ! AtLeastOnceDelivery.Confirm
}

final case class Settle(
    clientId: ClientId,
    customerId: CustomerId,
    walletShopId: WalletShopId,
    orderId: OrderId,
    amountTran: AmountTran,
    replyTo: ActorRef[SettlementResponse],
    confirmTo: ActorRef[AtLeastOnceDelivery.Confirm],
)(implicit val appRequestContext: AppRequestContext)
    extends BusinessCommand
    with AtLeastOnceDeliveryAcceptable

final case class Cancel(
    clientId: ClientId,
    customerId: CustomerId,
    walletShopId: WalletShopId,
    orderId: OrderId,
    replyTo: ActorRef[SettlementResponse],
    confirmTo: ActorRef[AtLeastOnceDelivery.Confirm],
)(implicit val appRequestContext: AppRequestContext)
    extends BusinessCommand
    with AtLeastOnceDeliveryAcceptable

private[actor] sealed trait InnerBusinessCommand extends BusinessCommand {
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
