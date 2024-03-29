package jp.co.tis.lerna.payment.application.ecpayment.issuing

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.util.Timeout
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.IssuingServiceECPaymentApplication
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._
import jp.co.tis.lerna.payment.adapter.issuing.IssuingServiceGateway
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.PaymentActor
import jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.PaymentActor.{ Cancel, Command, Settle, Sharding }
import jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantShardingSupport
import jp.co.tis.lerna.payment.readmodel.JDBCService
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.utility.AppRequestContext
import lerna.util.akka.AtLeastOnceDelivery
import lerna.util.time.JavaDurationConverters._
import lerna.util.time.LocalDateTimeFactory

import scala.concurrent.Future

/** ハウスマニー決済要求API applicationの実装
  * @param gateway　IssuingService API呼び出し用
  * @param config　設定ファイル
  * @param system actorシステム
  * @param database RDBMSアクセス
  * @param tables RDBMSテーブルアクセス
  * @param dateTimeFactory システム取得用
  * @param transactionIdFactory　取引ID採番
  * @param paymentIdFactory　(会員ごと)決済番号採番
  */
class IssuingServiceECPaymentApplicationImpl(
    gateway: IssuingServiceGateway,
    config: Config,
    database: JDBCService,
    tables: Tables,
    dateTimeFactory: LocalDateTimeFactory,
    transactionIdFactory: TransactionIdFactory,
    paymentIdFactory: PaymentIdFactory,
)(implicit val system: ActorSystem[Nothing])
    extends IssuingServiceECPaymentApplication {

  import system.executionContext

  implicit val timeout: Timeout =
    Timeout.durationToTimeout(
      config.getDuration("jp.co.tis.lerna.payment.application.ecpayment.issuing.payment-timeout").asScala,
    )

  override def pay(
      paymentParameter: PaymentParameter,
  )(implicit appRequestContext: AppRequestContext): Future[SettlementSuccessResponse] = {
    AtLeastOnceDelivery
      .askTo[ShardingEnvelope[Command], SettlementResponse](
        destination = shardRegion,
        (replyTo, confirmTo) => {
          val command = Settle(
            paymentParameter.clientId,
            paymentParameter.customerId,
            paymentParameter.walletShopId,
            paymentParameter.orderId,
            paymentParameter.amountTran,
            replyTo,
            confirmTo,
          )
          val entityId = PaymentActor.entityId(
            paymentParameter.clientId,
            paymentParameter.walletShopId,
            paymentParameter.orderId,
          )
          val tenantSupportEntityId = MultiTenantShardingSupport.tenantSupportEntityId(entityId)
          ShardingEnvelope[Command](tenantSupportEntityId, command)
        },
      ).flatMap {
        case successResponse: SettlementSuccessResponse => Future.successful(successResponse)
        case SettlementFailureResponse(message)         => Future.failed(new BusinessException(message))
      }
  }

  override def cancel(
      paymentCancelParameter: PaymentCancelParameter,
  )(implicit appRequestContext: AppRequestContext): Future[SettlementSuccessResponse] = {
    AtLeastOnceDelivery
      .askTo[ShardingEnvelope[Command], SettlementResponse](
        destination = shardRegion,
        (replyTo, confirmTo) => {
          val command = Cancel(
            paymentCancelParameter.clientId,
            paymentCancelParameter.customerId,
            paymentCancelParameter.walletShopId,
            paymentCancelParameter.orderId,
            replyTo,
            confirmTo,
          )
          val entityId = PaymentActor.entityId(
            paymentCancelParameter.clientId,
            paymentCancelParameter.walletShopId,
            paymentCancelParameter.orderId,
          )
          val tenantSupportEntityId = MultiTenantShardingSupport.tenantSupportEntityId(entityId)
          ShardingEnvelope[Command](tenantSupportEntityId, command)
        },
      ).flatMap {
        case successResponse: SettlementSuccessResponse => Future.successful(successResponse)
        case SettlementFailureResponse(message)         => Future.failed(new BusinessException(message))
      }
  }

  private val shardRegion =
    Sharding.startClusterSharding(
      gateway,
      database,
      tables,
      dateTimeFactory,
      transactionIdFactory,
      paymentIdFactory,
    )
}
