package jp.co.tis.lerna.payment.application.ecpayment.issuing

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.IssuingServiceECPaymentApplication
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._
import jp.co.tis.lerna.payment.adapter.issuing.IssuingServiceGateway
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.PaymentActor.Sharding
import jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.{ Cancel, Settle }
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
)(implicit val system: ActorSystem)
    extends IssuingServiceECPaymentApplication {

  import system.dispatcher

  implicit val timeout: Timeout =
    Timeout.durationToTimeout(
      config.getDuration("jp.co.tis.lerna.payment.application.ecpayment.issuing.payment-timeout").asScala,
    )

  override def pay(
      paymentParameter: PaymentParameter,
  )(implicit appRequestContext: AppRequestContext): Future[SettlementSuccessResponse] = {

    val command = Settle(
      paymentParameter.clientId,
      paymentParameter.customerId,
      paymentParameter.walletShopId,
      paymentParameter.orderId,
      paymentParameter.amountTran,
    )
    AtLeastOnceDelivery.askTo(destination = shardRegion, command).mapTo[SettlementResponse].flatMap {
      case successResponse: SettlementSuccessResponse => Future.successful(successResponse)
      case SettlementFailureResponse(message)         => Future.failed(new BusinessException(message))
    }
  }

  override def cancel(
      paymentCancelParameter: PaymentCancelParameter,
  )(implicit appRequestContext: AppRequestContext): Future[SettlementSuccessResponse] = {
    val command = Cancel(
      paymentCancelParameter.clientId,
      paymentCancelParameter.customerId,
      paymentCancelParameter.walletShopId,
      paymentCancelParameter.orderId,
    )
    AtLeastOnceDelivery.askTo(destination = shardRegion, command).mapTo[SettlementResponse].flatMap {
      case successResponse: SettlementSuccessResponse => Future.successful(successResponse)
      case SettlementFailureResponse(message)         => Future.failed(new BusinessException(message))
    }
  }

  private val shardRegion =
    Sharding.startClusterSharding(
      config,
      gateway,
      database,
      tables,
      dateTimeFactory,
      transactionIdFactory,
      paymentIdFactory,
    )
}
