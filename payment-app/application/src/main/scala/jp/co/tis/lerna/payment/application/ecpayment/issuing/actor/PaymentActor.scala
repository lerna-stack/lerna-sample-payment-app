package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import java.time
import java.time.LocalDateTime

import akka.actor.{ ActorRef, ActorSystem, Cancellable, Props, ReceiveTimeout }
import akka.cluster.Cluster
import akka.cluster.sharding.ShardRegion.EntityId
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings, ShardRegion }
import akka.persistence.PersistentActor
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.ecpayment.model.WalletShopId
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._
import jp.co.tis.lerna.payment.adapter.issuing.IssuingServiceGateway
import jp.co.tis.lerna.payment.adapter.issuing.model.{
  AcquirerReversalRequestParameter,
  AuthorizationRequestParameter,
  IssuingServiceResponse,
}
import jp.co.tis.lerna.payment.adapter.util._
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.adapter.wallet.{ ClientId, CustomerId, WalletId }
import jp.co.tis.lerna.payment.application.ActorPrefix
import jp.co.tis.lerna.payment.application.ecpayment.issuing.{
  IssuingServicePayCredential,
  PaymentIdFactory,
  TransactionIdFactory,
}
import jp.co.tis.lerna.payment.application.util.persistence.actor.MultiTenantShardingPersistenceIdHelper
import jp.co.tis.lerna.payment.application.util.tenant.actor.{
  MultiTenantPersistentSupport,
  MultiTenantShardingSupport,
}
import jp.co.tis.lerna.payment.readmodel.JDBCService
import jp.co.tis.lerna.payment.readmodel.constant.{ LogicalDeleteFlag, ServiceRelationForeignKeyType }
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.utility.AppRequestContext
import lerna.log.AppActorLogging
import lerna.util.akka
import lerna.util.akka.AtLeastOnceDelivery.AtLeastOnceDeliveryRequest
import lerna.util.akka.{ ActorStateBase, ProcessingTimeout, ReplyTo }
import lerna.util.lang.Equals._
import lerna.util.time.LocalDateTimeFactory
import lerna.util.trace.TraceId

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

/** アクターのコンパニオンオブジェクト
  */
object PaymentActor {

  object Sharding {

    val typeName: String = ActorPrefix.Ec.houseMoney

    def startClusterSharding(
        config: Config,
        gateway: IssuingServiceGateway,
        jdbcService: JDBCService,
        tables: Tables,
        dateTimeFactory: LocalDateTimeFactory,
        transactionIdFactory: TransactionIdFactory,
        paymentIdFactory: PaymentIdFactory,
    )(implicit
        system: ActorSystem,
    ): ActorRef = {
      def calculateEntityId(command: Command): EntityId =
        s"${command.clientId.value}-${command.walletShopId.value}-${command.orderId.value}"

      val extractEntityId: ShardRegion.ExtractEntityId = {
        case request @ AtLeastOnceDeliveryRequest(command: Command) =>
          val entityId = MultiTenantShardingSupport.tenantSupportEntityId(command, calculateEntityId)
          (entityId, request)
      }

      val numberOfShards = 100

      val extractShardId: ShardRegion.ExtractShardId = {
        case AtLeastOnceDeliveryRequest(command: Command) =>
          Math.abs(calculateEntityId(command).hashCode % numberOfShards).toString
      }

      val clusterSharding         = ClusterSharding(system)
      val clusterShardingSettings = ClusterShardingSettings(system)

      clusterSharding.start(
        typeName = typeName,
        entityProps = PaymentActor
          .props(
            config,
            gateway,
            jdbcService,
            tables,
            dateTimeFactory,
            transactionIdFactory,
            paymentIdFactory,
          ),
        settings = clusterShardingSettings,
        extractEntityId = extractEntityId,
        extractShardId = extractShardId,
        allocationStrategy = clusterSharding.defaultShardAllocationStrategy(clusterShardingSettings),
        handOffStopMessage = StopActor,
      )

      // ShardRegion の Graceful shutdown 時用
      // ShardRegion の shutdown 時に ShardRegion に転送されたメッセージは drop される可能性があるため、proxy 経由とすることで drop を回避する
      // ShardRegionProxy ではなく ShardRegion 宛に送ると、リトライがあっても ShardRegion が終了していると DeadLetter になる点に注意
      // ※ Akka 2.5.22 時点での実装依存(SelfDataCenter)
      ClusterSharding(system).startProxy(
        typeName = typeName,
        role = None,
        dataCenter = Option(Cluster(system).settings.SelfDataCenter),
        extractEntityId = extractEntityId,
        extractShardId = extractShardId,
      )
    }
  }

  def props(
      config: Config,
      paymentGateway: IssuingServiceGateway,
      jdbcService: JDBCService,
      tables: Tables,
      dateTimeFactory: LocalDateTimeFactory,
      transactionIdFactory: TransactionIdFactory,
      paymentIdFactory: PaymentIdFactory,
  ): Props =
    Props(
      new PaymentActor(
        config,
        paymentGateway,
        jdbcService,
        tables,
        dateTimeFactory,
        transactionIdFactory,
        paymentIdFactory,
      ),
    )

}

/** アクタークラス
  *
  * @param config 設定ファイルアクセス用
  * @param gateway 外部システム呼び出し用
  * @param jdbcService RDBMSアクセス用
  * @param tables RDBMSテーブルアクセス用
  * @param dateTimeFactory システム日時取得用
  * @param transactionIdFactory 取引ID採番
  * @param paymentIdFactory　(会員ごと)決済番号採番
  */
class PaymentActor(
    config: Config,
    gateway: IssuingServiceGateway,
    jdbcService: JDBCService,
    tables: Tables,
    dateTimeFactory: LocalDateTimeFactory,
    transactionIdFactory: TransactionIdFactory,
    paymentIdFactory: PaymentIdFactory,
) extends PersistentActor
    with MultiTenantPersistentSupport
    with MultiTenantShardingSupport
    with MultiTenantShardingPersistenceIdHelper
    with AppActorLogging {

  import lerna.util.time.JavaDurationConverters._

  // actor(self)が終了していると、context === null となり context.dispatcher を取得できなく Future#map が NPE となる問題対策で、 dispatcher を変数に束縛しておく
  implicit private val dispatcher: ExecutionContext = context.dispatcher

  override def persistenceIdPrefix: String = ActorPrefix.Ec.houseMoney

  val receiveTimeout: time.Duration =
    context.system.settings.config
      .getDuration("jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.receive-timeout")

  context.setReceiveTimeout(receiveTimeout.asScala)

  val askTimeout: FiniteDuration = context.system.settings.config
    .getDuration("jp.co.tis.lerna.payment.application.ecpayment.issuing.payment-timeout").asScala

  private val errCodeOk = "00000"

  /** 状態遷移
    *
    * @param event 遷移先イベント
    */
  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  private def updateState(event: ECPaymentIssuingServiceEvent): Unit = {
    state = state.updated.lift.apply(event).getOrElse {
      implicit val appRequestContext: AppRequestContext = AppRequestContext(TraceId.unknown, tenant)
      logger.error("Unexpected event processed: {}", event)
      state
    }
    context.become(state.receiveCommand)
  }
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private var state: State = WaitingForRequest()

  // 初期振る舞いイベントハンドラー
  override def receiveCommand: Receive = state.receiveCommand

  sealed trait State extends ActorStateBase[ECPaymentIssuingServiceEvent, State]

  case class WaitingForRequest() extends State {
    override def updated: EventHandler = {
      case event: SettlementAccepted =>
        import event.traceId

        val processingTimeoutMessage: ProcessingTimeout =
          ProcessingTimeout(event.systemTime, askTimeout, context.system.settings.config)
        val processingTimeoutTimer: Cancellable = context.system.scheduler
          .scheduleOnce(
            delay = processingTimeoutMessage.timeLeft(dateTimeFactory),
            receiver = self,
            message = processingTimeoutMessage,
          )

        Settling(
          event.requestInfo,
          event.systemTime,
          processingTimeoutMessage,
          processingTimeoutTimer,
        )
    }

    override def receiveCommand: Receive = {
      case request @ AtLeastOnceDeliveryRequest(cancelRequest: Cancel) =>
        request.accept()

        import cancelRequest.appRequestContext
        val msg = NotFound("決済情報")
        logger.debug(
          s"${msg.messageId} ${msg.messageContent} Cancel request id ${cancelRequest.walletShopId.value} is not found!",
        )
        replyAndStopSelf(
          sender,
          SettlementFailureResponse(msg),
        )

      case request @ AtLeastOnceDeliveryRequest(payRequest: Settle) =>
        import payRequest.appRequestContext
        logger.debug(s"IssuingService : $payRequest")

        // リクエスト直前の時刻をこちらで作成（変数化）
        val systemTime = dateTimeFactory.now()

        implicit val processingContext: ProcessingContext =
          ProcessingContext(
            clientId = payRequest.clientId,
            walletShopId = payRequest.walletShopId,
            orderId = payRequest.orderId,
            replyTo = ReplyTo(sender()),
          )

        persist(SettlementAccepted(payRequest, systemTime)) { event =>
          updateState(event)

          request.accept()

          val resultFuture = executePayment(payRequest, systemTime)

          resultFuture onComplete { triedResult =>
            val result: Either[
              OnlineProcessingFailureMessage,
              (
                  IssuingServicePayCredential,
                  AuthorizationRequestParameter,
                  Either[OnlineProcessingFailureMessage, IssuingServiceResponse],
              ),
            ] = triedResult.toEither.left.map(handleException)
            sendToSelf(SettlementResult(result))
          }
        }
    }
  }

  private def executePayment(
      payRequest: Settle,
      systemTime: LocalDateTime,
  )(implicit
      appRequestContext: AppRequestContext,
  ) = {
    val customerId = payRequest.customerId

    for {
      payCredential <- fetchPayCredential(customerId, payRequest.clientId, payRequest.walletShopId)
      transactionId <- transactionIdFactory.generate
      paymentId     <- paymentIdFactory.generateIdFor(customerId)
      request = {
        logger.debug("walletId:" + payCredential.walletId.toString)
        logger.debug("customerNumber:" + payCredential.customerNumber.toString)
        logger.debug("memberStoreNameId:" + payCredential.memberStoreId)
        logger.debug("memberStoreNameEn:" + payCredential.memberStoreNameEn.toString)
        logger.debug("memberStoreNameJp:" + payCredential.memberStoreNameJp.toString)
        logger.debug("contractNumber:" + payCredential.contractNumber.toString)
        logger.debug("terminalId:" + payCredential.terminalId.value)

        AuthorizationRequestParameter(
          pan = payCredential.housePan,                           // カード番号
          amountTran = payRequest.amountTran,                     // 取引金額
          tranDateTime = systemTime,                              // 送信日時
          transactionId = transactionId,                          // 取引ID
          accptrId = "%-15s".format(payCredential.memberStoreId), // 加盟店ID 左詰め１５桁固定長
          paymentId = paymentId,                                  // (会員ごと)決済番号
          terminalId = payCredential.terminalId,                  // 端末識別番号
        )

      }
      result: Either[OnlineProcessingFailureMessage, IssuingServiceResponse] <- gateway
        .requestAuthorization(request).transform {
          // Gatewayエラーの場合のみ、非同期処理で、RDBMSに登録必要
          case Success(response) =>
            Success(Right(response))

          case Failure(ex: BusinessException) =>
            Success(Left(ex.message))

          case Failure(exception) =>
            val message = UnpredictableError()
            logger.warn(exception, "{}: {}", message.messageId, message.messageContent)
            Success(Left(message))
        }
    } yield (payCredential, request, result)
  }

  case class Settling(
      requestInfo: Settle,
      systemTime: LocalDateTime,
      processingTimeoutMessage: ProcessingTimeout,
      processingTimeoutTimer: Cancellable,
  ) extends State {
    override def updated: EventHandler = {
      // 決済成功
      case event: SettlementSuccessConfirmed =>
        Completed(
          event.successResponse,
          event.payCredential,
          event.requestInfo.customerId,
          event.paymentResponse,
          event.issuingServiceRequest,
          event.systemDate,
        )

      // 決済要求リクエスト前に失敗
      case event: SettlementAborted =>
        Failed(event.failureMessage)

      // 決済失敗
      case event: SettlementFailureConfirmed =>
        Failed(event.failureMessage)

      case _: SettlementTimeoutDetected =>
        val message = UnpredictableError()
        Failed(message)

    }

    override def receiveCommand: Receive = stashStopActorMessage orElse {
      case paymentResult: SettlementResult =>
        import paymentResult.{ appRequestContext, processingContext }

        processingTimeoutTimer.cancel()

        paymentResult.result match {
          case Right((payCredential, req, result)) =>
            result match {
              case Right(response) =>
                response.rErrcode match {
                  case `errCodeOk` =>
                    val res = SettlementSuccessResponse()
                    logger.debug(
                      s"status ok: paymentProcessing, systemTime: $systemTime ",
                    )

                    val event =
                      SettlementSuccessConfirmed(
                        response,
                        payCredential,
                        requestInfo,
                        res,
                        req,
                        systemTime,
                      )

                    // 処理が完了の時点で、たまったPay Commandをunstash
                    // 目的：処理中で受けったPay Commandに対し、同じ処理結果を返すため
                    persistAndReply(event, res) {
                      // do nothing
                    }
                  case errorCode =>
                    // 承認売上送信エラーなし(200) でも エラーコード "00000"以外、エラーにする
                    logger.debug(
                      s"status payment failed, payment response 200 ok, error code is not 00000: paymentProcessing",
                    )

                    val message = IssuingServiceServerError("承認売上送信", errorCode)
                    logger.warn(s"${message.messageId}: ${message.messageContent}")

                    val event =
                      SettlementFailureConfirmed(
                        Option(response),
                        payCredential,
                        requestInfo,
                        req,
                        message,
                        systemTime,
                      )

                    persistAndReply(event, SettlementFailureResponse(message)) {
                      // do nothing
                    }
                }

              case Left(message) =>
                persistAndReply(
                  SettlementFailureConfirmed(
                    None,
                    payCredential,
                    requestInfo,
                    req,
                    message,
                    systemTime,
                  ),
                  SettlementFailureResponse(message),
                ) {
                  // do nothing
                }
            }

          // Gatewayから何のレスポンスEntity(JSON)もなし
          // 承認売上も、障害取消も
          case Left(message) =>
            persistAndReply(
              SettlementAborted(
                message,
                systemTime,
              ),
              SettlementFailureResponse(message),
            ) {
              // do nothing
            }
        }

      case AtLeastOnceDeliveryRequest(msg: Settle) =>
        import msg.appRequestContext
        stash()
        logger.info("前回のリクエストが処理中のため一時的に保留(stash)します")

      case `processingTimeoutMessage` =>
        import processingTimeoutMessage.requestContext
        logger.info("処理タイムアウトしました: {}", processingTimeoutMessage)
        persist(SettlementTimeoutDetected()(requestContext.traceId)) { event =>
          updateState(event)
          unstashAll()
          stopSelfSafely()
        }
    }
  }

  case class Completed(
      settlementSuccessResponse: SettlementSuccessResponse,
      payCredential: IssuingServicePayCredential,
      customerId: CustomerId,
      paymentResponse: IssuingServiceResponse,
      originalRequestParameter: AuthorizationRequestParameter,
      saleDateTime: LocalDateTime,
  ) extends State {
    override def updated: EventHandler = {
      // 取消
      case event: CancelAccepted =>
        import event.traceId

        val processingTimeoutMessage: ProcessingTimeout =
          ProcessingTimeout(event.systemDateTime, askTimeout, context.system.settings.config)
        val processingTimeoutTimer: Cancellable = context.system.scheduler
          .scheduleOnce(
            delay = processingTimeoutMessage.timeLeft(dateTimeFactory),
            receiver = self,
            message = processingTimeoutMessage,
          )

        Canceling(
          event.requestInfo,
          settlementSuccessResponse,
          payCredential,
          paymentResponse,
          originalRequestParameter,
          saleDateTime,
          event.systemDateTime,
          processingTimeoutMessage,
          processingTimeoutTimer,
        )
    }

    override def receiveCommand: Receive = {
      case request @ AtLeastOnceDeliveryRequest(msg: Settle) =>
        import msg.appRequestContext
        request.accept()
        logger.info("すでに処理済みのため、前回の処理結果を返します(前回とキーが同じリクエストが来ました)")

        replyAndStopSelf(sender(), settlementSuccessResponse)

      case request @ AtLeastOnceDeliveryRequest(msg: Cancel) =>
        import msg.appRequestContext
        // リクエスト直前の時刻をこちらで作成（変数化）
        val systemTime = dateTimeFactory.now()

        implicit val processingContext: ProcessingContext =
          ProcessingContext(
            clientId = msg.clientId,
            walletShopId = msg.walletShopId,
            orderId = msg.orderId,
            replyTo = akka.ReplyTo(sender()),
          )

        persist(
          CancelAccepted(
            msg,
            systemTime,
          ),
        ) { event =>
          updateState(event)

          request.accept()

          val resultFuture = executeCancel(msg, customerId, originalRequestParameter, systemTime)
          resultFuture onComplete { triedResult =>
            val result = triedResult.toEither.left.map(handleException)
            sendToSelf(CancelResult(result))
          }
        }

    }
  }

  private def executeCancel(
      issuingServiceCancel: Cancel,
      customerId: CustomerId,
      originalRequestParameter: AuthorizationRequestParameter,
      systemTime: LocalDateTime,
  )(implicit
      appRequestContext: AppRequestContext,
  ) = {
    for {
      payCredential <- fetchPayCredential(customerId, issuingServiceCancel.clientId, issuingServiceCancel.walletShopId)
      paymentId     <- paymentIdFactory.generateIdFor(customerId)
      transactionId <- transactionIdFactory.generate
      acquirerReversalRequestParameter = AcquirerReversalRequestParameter(
        transactionId = transactionId,        // 取引ID、採番
        paymentId = paymentId,                // (会員ごと)決済番号
        terminalId = payCredential.terminalId,// 端末識別番号
      )
      issuingServicePaymentResult: Either[OnlineProcessingFailureMessage, IssuingServiceResponse] <- gateway
        .requestAcquirerReversal(acquirerReversalRequestParameter, originalRequestParameter).transform {
          // Gatewayエラーの場合のみ、非同期処理で、RDBMSに登録必要
          case Success(response) =>
            Success(Right(response))

          case Failure(ex: BusinessException) =>
            Success(Left(ex.message))

          case Failure(exception) =>
            val message = UnpredictableError()
            logger.warn(exception, "{}: {}", message.messageId, message.messageContent)
            Success(Left(message))
        }
    } yield (
      issuingServicePaymentResult,
      payCredential,
      acquirerReversalRequestParameter,
    )
  }

  case class Canceling(
      requestInfo: Cancel,
      settlementSuccessResponse: SettlementSuccessResponse, // 決済成功時、actor -> presentationのレスポンス
      credential: IssuingServicePayCredential,              // RDBMSからの認証情報
      paymentResponse: IssuingServiceResponse,              // IssuingService からのレスポンス
      originalRequest: AuthorizationRequestParameter,       // IssuingService への決済リクエスト
      saleDateTime: LocalDateTime,                          // 買上日時、※決済要求時のシステム日時
      systemDateTime: LocalDateTime,
      processingTimeoutMessage: ProcessingTimeout,
      processingTimeoutTimer: Cancellable,
  ) extends State {
    override def updated: EventHandler = {
      // 取消成功
      case event: CancelSuccessConfirmed =>
        Canceled(
          event.cancelSuccessResponse,
          event.requestInfo.customerId,
          event.cancelResponse,
        )

      // 取消リクエスト前失敗、決済成功の状態に戻る
      case _: CancelAborted =>
        Completed(
          settlementSuccessResponse,
          credential,
          requestInfo.customerId,
          paymentResponse,
          originalRequest,
          saleDateTime,
        )

      // 取消失敗、決済成功の状態に戻る
      case event: CancelFailureConfirmed =>
        Completed(
          settlementSuccessResponse,
          event.payCredential,
          event.requestInfo.customerId,
          event.paymentResponse,
          event.originalRequest,
          event.saleDateTime,
        )

      case _: CancelTimeoutDetected =>
        Completed(
          settlementSuccessResponse,
          credential,
          requestInfo.customerId,
          paymentResponse,
          originalRequest,
          saleDateTime,
        )
    }

    override def receiveCommand: Receive =
      stashStopActorMessage orElse
      handleCancelProcessingTimeoutMessage(processingTimeoutMessage) orElse {
        case cancelResult: CancelResult =>
          processingTimeoutTimer.cancel()
          import cancelResult.{ appRequestContext, processingContext }
          cancelResult.result match {
            case Right((result, payCredential, acquirerReversalRequestParameter)) =>
              result match {
                case Right(response) =>
                  response.rErrcode match {
                    case `errCodeOk` => // エラーコード 正常
                      val res = SettlementSuccessResponse()
                      logger.debug(
                        s"status ok: cancelProcessing",
                      )
                      val event = CancelSuccessConfirmed(
                        response,
                        payCredential,
                        requestInfo,
                        res,
                        paymentResponse.intranid,
                        saleDateTime,
                        acquirerReversalRequestParameter,
                        originalRequest,
                        systemDateTime,
                      )

                      persistAndReply(event, res) {
                        // do nothing
                      }

                    case "TW005" => // 取消対象取引が既に取消済
                      val res = SettlementSuccessResponse()
                      logger.debug(
                        s"status payment's already been canceled: cancelProcessing",
                      )
                      val event = CancelSuccessConfirmed(
                        response,
                        payCredential,
                        requestInfo,
                        res,
                        paymentResponse.intranid,
                        saleDateTime,
                        acquirerReversalRequestParameter,
                        originalRequest,
                        systemDateTime,
                      )
                      val message = IssuingServiceAlreadyCanceled()
                      logger.debug(s"${message.messageId}: ${message.messageContent}")
                      persistAndReply(event, SettlementFailureResponse(message)) {
                        // do nothing
                      }

                    case errorCode =>
                      logger.debug(
                        s"status cancel failed, error code is not 00000: cancelProcessing",
                      )

                      val message = IssuingServiceServerError("承認取消送信", errorCode)
                      logger.warn(s"${message.messageId}: ${message.messageContent}")
                      val event =
                        CancelFailureConfirmed(
                          paymentResponse,
                          requestInfo,
                          payCredential,
                          Option(response),
                          message,
                          originalRequest,
                          acquirerReversalRequestParameter,
                          saleDateTime,
                          systemDateTime,
                        )
                      persistAndReply(event, SettlementFailureResponse(message)) {
                        // do nothing
                      }
                  }

                case Left(message) =>
                  val cancelFailedEvent =
                    CancelFailureConfirmed(
                      paymentResponse,
                      requestInfo,
                      payCredential,
                      None,
                      message,
                      originalRequest,
                      acquirerReversalRequestParameter,
                      saleDateTime,
                      systemDateTime,
                    )

                  persistAndReply(cancelFailedEvent, SettlementFailureResponse(message)) {
                    // do nothing
                  }
              }

            case Left(message) =>
              // 非同期処理対象外
              val cancelFailedEvent =
                CancelAborted()
              persistAndReply(cancelFailedEvent, SettlementFailureResponse(message)) {
                // do nothing
              }
          }

        case AtLeastOnceDeliveryRequest(msg: Cancel) =>
          import msg.appRequestContext
          stash()
          logger.info("前回のリクエストが処理中のため一時的に保留(stash)します")

      }
  }

  private def handleCancelProcessingTimeoutMessage(
      processingTimeoutMessage: ProcessingTimeout,
  ): Receive = {
    case `processingTimeoutMessage` =>
      import processingTimeoutMessage.requestContext
      logger.info("処理タイムアウトしました: {}", processingTimeoutMessage)
      persist(
        CancelTimeoutDetected()(requestContext.traceId),
      ) { event =>
        updateState(event)
        unstashAll()
        stopSelfSafely()
      }
  }

  // 永続化およびPresentationへの返事
  private def persistAndReply(event: ECPaymentIssuingServiceEvent, msg: SettlementResponse)(afterPersist: => Unit)(
      implicit processingContext: ProcessingContext,
  ): Unit = {
    // 処理が完了の時点で、たまったPay Commandをunstash
    // 目的：処理中で受けったPay Commandに対し、同じ処理結果を返すため
    persist(event) { e =>
      updateState(e)
      afterPersist
      replyResult(msg)
    }
  }

  case class Canceled(
      cancelSuccessResponse: SettlementSuccessResponse,
      customerId: CustomerId,
      cancelResponse: IssuingServiceResponse,
  ) extends State {
    override def updated: EventHandler = PartialFunction.empty

    override def receiveCommand: Receive = {
      case request @ AtLeastOnceDeliveryRequest(msg: Cancel) if msg.customerId === customerId =>
        import msg.appRequestContext
        request.accept()
        logger.info("すでに処理済みのため、前回の処理結果を返します(前回とキーが同じリクエストが来ました)")

        replyAndStopSelf(sender(), cancelSuccessResponse)
    }
  }

  case class Failed(
      message: OnlineProcessingFailureMessage,
  ) extends State {
    override def updated: EventHandler = PartialFunction.empty

    override def receiveCommand: Receive = {
      case request @ AtLeastOnceDeliveryRequest(msg: Settle) =>
        import msg.appRequestContext
        request.accept()
        logger.info("すでに処理済みのため、前回の処理結果を返します(前回とキーが同じリクエストが来ました)")

        logger.info(
          s"status: failed, msg: $msg",
        )
        replyAndStopSelf(sender(), SettlementFailureResponse(message))
    }
  }

  /** 決済情報取得
    *
    * @param customerId カスターマーID
    * @param clientId クライアントID
    * @return 決済情報
    */
  @SuppressWarnings(
    Array(
      "org.wartremover.warts.OptionPartial",
      "org.wartremover.contrib.warts.SomeApply",
    ),
  )
  private def fetchPayCredential(customerId: CustomerId, clientId: ClientId, walletShopId: WalletShopId)(implicit
      appRequestContext: AppRequestContext,
  ): Future[IssuingServicePayCredential] = {
    import tables.profile.api._

    val query = for {
      c <- tables.Customer
        .filter(_.customerId === customerId.value)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted)
      hms <- tables.HouseMemberStore
        .filter(_.clientId === clientId.value.toString)
        .filter(_.walletShopId === walletShopId.value)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted)
      s <- tables.ServiceRelation
        .filter(_.customerId === customerId.value)
        .filter(_.foreignKeyType === ServiceRelationForeignKeyType.IssuingService)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted)
      issuingService <- tables.IssuingService
        .filter(_.serviceRelationId === s.serviceRelationId)
        .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted)
    } yield (
      c.walletId,
      c.customerNumber,
      issuingService.contractNumber,
      issuingService.housePan,
      hms.memberStoreId,
      hms.memberStoreNameEn,
      hms.memberStoreNameJp,
      hms.terminalId.mapTo[TerminalId],
    )

    val action = query.result.headOption

    jdbcService.db.run(action).map {
      case Some(
            (
              walletId,
              customerNumber,
              contractNumber,
              Some(housePan),
              memberStoreId,
              memberStoreNameEn,
              memberStoreNameJp,
              terminalId,
            ),
          ) =>
        IssuingServicePayCredential(
          walletId = walletId.map(WalletId.apply),
          customerNumber = customerNumber,
          memberStoreId = memberStoreId,
          memberStoreNameEn = memberStoreNameEn,
          memberStoreNameJp = memberStoreNameJp,
          contractNumber = contractNumber.toString,
          housePan = HousePan(housePan),
          terminalId = terminalId,
        )

      case _ =>
        val message = NotFound("決済情報")
        logger.debug(s"${message.messageId}: ${message.messageContent}")
        throw new BusinessException(message)
    }
  }

  /** 未知の異常をUnpredictableErrorに変換するため
    *
    * @param cause 異常
    */
  private def handleException(
      cause: Throwable,
  )(implicit appRequestContext: AppRequestContext): OnlineProcessingFailureMessage = {
    cause match {
      case exception: BusinessException => exception.message
      case ex =>
        val message = UnpredictableError()
        logger.warn(ex, "{}: {}", message.messageId, message.messageContent)
        message
    }
  }

  override def receiveRecover: Receive = {
    case event: ECPaymentIssuingServiceEvent => updateState(event)
  }

  private def stashStopActorMessage: Receive = {
    case StopActor =>
      import lerna.util.tenant.TenantComponentLogContext.logContext
      logger.info(s"[state: $state, receive: StopActor] 処理結果待ちのため終了処理を保留します")
      stash()
  }

  // 未処理のメッセージ
  override def unhandled(message: Any): Unit = {
    message match {
      case StopActor =>
        implicit val traceId: TraceId = TraceId.unknown
        logger.debug(s"state: $state, receive: StopActor")
        context.stop(self)
      case request @ AtLeastOnceDeliveryRequest(payRequest: Settle) => // DOS攻撃防止のため
        request.accept()
        val msg = ValidationFailure("walletShopId または orderId が不正です")
        replyAndStopSelf(sender, SettlementFailureResponse(msg))
      case request @ AtLeastOnceDeliveryRequest(payRequest: Cancel) => // DOS攻撃防止のため
        request.accept()
        val msg = ValidationFailure("walletShopId または orderId が不正です")
        replyAndStopSelf(sender, SettlementFailureResponse(msg))
      case ReceiveTimeout =>
        implicit val appRequestContext: AppRequestContext = AppRequestContext(TraceId.unknown, tenant)
        logger.info("Actorの生成から一定時間経過しました。Actorを停止します。")
        stopSelfSafely()
      case _: ProcessingTimeout => // ignore
      case m =>
        super.unhandled(m)
    }
  }

  private def sendToSelf(message: InnerCommand): Unit = {
    self ! message
  }

  /** 応答とその他処理
    * @param result 応答メッセージ
    */
  private def replyResult(result: SettlementResponse)(implicit processingContext: ProcessingContext): Unit = {
    unstashAll()
    replyAndStopSelf(processingContext.replyTo.actorRef, result)
  }

  // 返事およびアウターストップ
  private def replyAndStopSelf(replyTo: ActorRef, msg: SettlementResponse): Unit = {
    replyTo ! msg
    stopSelfSafely()
  }

  private def stopSelfSafely(): Unit = {
    context.parent ! ShardRegion.Passivate(stopMessage = StopActor)
  }
}
