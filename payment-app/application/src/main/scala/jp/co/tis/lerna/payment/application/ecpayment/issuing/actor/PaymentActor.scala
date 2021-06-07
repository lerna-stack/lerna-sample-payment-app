package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityContext, EntityTypeKey }
import akka.cluster.sharding.typed.{ HashCodeMessageExtractor, ShardingEnvelope }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._
import jp.co.tis.lerna.payment.adapter.ecpayment.model.WalletShopId
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
import jp.co.tis.lerna.payment.application.util.tenant.actor.{
  MultiTenantPersistentSupport,
  MultiTenantShardingSupport,
}
import jp.co.tis.lerna.payment.readmodel.JDBCService
import jp.co.tis.lerna.payment.readmodel.constant.{ LogicalDeleteFlag, ServiceRelationForeignKeyType }
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.utility.AppRequestContext
import lerna.log.{ AppLogger, AppTypedActorLogging }
import lerna.util.lang.Equals._
import lerna.util.time.LocalDateTimeFactory
import lerna.util.trace.TraceId

import java.time
import java.time.LocalDateTime
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

/** アクターのコンパニオンオブジェクト
  */
object PaymentActor extends AppTypedActorLogging {

  object Sharding {

    def startClusterSharding(
        config: Config,
        gateway: IssuingServiceGateway,
        jdbcService: JDBCService,
        tables: Tables,
        dateTimeFactory: LocalDateTimeFactory,
        transactionIdFactory: TransactionIdFactory,
        paymentIdFactory: PaymentIdFactory,
    )(implicit
        system: ActorSystem[Nothing],
    ): ActorRef[ShardingEnvelope[Command]] = {
      val numberOfShards = 100

      val clusterSharding = ClusterSharding(system)

      val shardRegion: ActorRef[ShardingEnvelope[Command]] =
        clusterSharding.init(
          Entity(EntityTypeKey[Command](ActorPrefix.Ec.houseMoney))(createBehavior = entityContext => {
            Behaviors.setup(context => {
              Behaviors.withTimers(timers => {
                withLogger(logger => {
                  val actor = new PaymentActor(
                    config,
                    gateway,
                    jdbcService,
                    tables,
                    dateTimeFactory,
                    transactionIdFactory,
                    paymentIdFactory,
                    context,
                    timers,
                    entityContext,
                    logger,
                  )
                  actor.eventSourcedBehavior()
                })
              })
            })
          })
            .withMessageExtractor(new HashCodeMessageExtractor(numberOfShards))
            .withStopMessage(StopActor),
        )

      shardRegion
    }
  }
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
    context: ActorContext[Command],
    timers: TimerScheduler[Command],
    val entityContext: EntityContext[Command],
    logger: AppLogger,
) extends MultiTenantPersistentSupport
    with MultiTenantShardingSupport[Command] {

  import context.executionContext
  import lerna.util.time.JavaDurationConverters._

  val receiveTimeout: time.Duration =
    context.system.settings.config
      .getDuration("jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.receive-timeout")

  context.setReceiveTimeout(receiveTimeout.asScala, ReceiveTimeout)

  val askTimeout: FiniteDuration = context.system.settings.config
    .getDuration("jp.co.tis.lerna.payment.application.ecpayment.issuing.payment-timeout").asScala

  private val errCodeOk = "00000"

  def eventSourcedBehavior(): EventSourcedBehavior[Command, ECPaymentIssuingServiceEvent, State] = {
    val persistenceId =
      PersistenceId.of(entityContext.entityTypeKey.name, originalEntityId)

    EventSourcedBehavior[Command, ECPaymentIssuingServiceEvent, State](
      persistenceId = persistenceId,
      emptyState = WaitingForRequest(),
      commandHandler = (state, command) => state.applyCommand(command),
      eventHandler = (state, event) => state.applyEvent(event),
    )
      .withJournalPluginId(journalPluginId(config))
      .withSnapshotPluginId(snapshotPluginId)
  }

  // type alias to reduce boilerplate
  type ReplyEffect = akka.persistence.typed.scaladsl.ReplyEffect[ECPaymentIssuingServiceEvent, State]

  // State
  sealed trait State {
    def applyEvent(event: ECPaymentIssuingServiceEvent): State
    def applyCommand(cmd: Command): ReplyEffect
  }

  case class WaitingForRequest() extends State {
    override def applyEvent(event: ECPaymentIssuingServiceEvent): State = event match {
      case event: SettlementAccepted =>
        import event.traceId

        val processingTimeoutMessage: ProcessingTimeout =
          ProcessingTimeout(event.systemTime, askTimeout, context.system.settings.config)

        timers.startSingleTimer(
          msg = processingTimeoutMessage,
          delay = processingTimeoutMessage.timeLeft(dateTimeFactory),
        )

        Settling(
          event.requestInfo,
          event.systemTime,
          processingTimeoutMessage,
        )

      case _ =>
        throw new IllegalArgumentException(
          s"この state(${this.getClass.getName}) では event(${event.getClass.getName}) は作成されない",
        )

    }

    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case cancelRequest: Cancel =>
        cancelRequest.accept()

        import cancelRequest.appRequestContext
        val msg = NotFound("決済情報")
        logger.debug(
          s"${msg.messageId} ${msg.messageContent} Cancel request id ${cancelRequest.walletShopId.value} is not found!",
        )
        replyAndStopSelf(
          cancelRequest.replyTo,
          SettlementFailureResponse(msg),
        )

      case payRequest: Settle =>
        import payRequest.appRequestContext
        logger.debug(s"IssuingService : $payRequest")

        // リクエスト直前の時刻をこちらで作成（変数化）
        val systemTime = dateTimeFactory.now()

        implicit val processingContext: ProcessingContext =
          ProcessingContext(
            clientId = payRequest.clientId,
            walletShopId = payRequest.walletShopId,
            orderId = payRequest.orderId,
            replyTo = payRequest.replyTo,
          )

        Effect
          .persist(SettlementAccepted(payRequest, systemTime))
          .thenRun((_: State) => {
            payRequest.accept()

            val resultFuture = executePayment(payRequest, systemTime)

            context.pipeToSelf(resultFuture) { triedResult =>
              val result: Either[
                OnlineProcessingFailureMessage,
                (
                    IssuingServicePayCredential,
                    AuthorizationRequestParameter,
                    Either[OnlineProcessingFailureMessage, IssuingServiceResponse],
                ),
              ] = triedResult.toEither.left.map(handleException)
              SettlementResult(result)
            }
          }).thenNoReply()

      case StopActor               => Effect.stop().thenNoReply()
      case ReceiveTimeout          => handleReceiveTimeout()
      case _: ProcessingTimeout    => Effect.unhandled.thenNoReply()
      case _: InnerBusinessCommand => Effect.unhandled.thenNoReply()
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
  ) extends State {
    override def applyEvent(event: ECPaymentIssuingServiceEvent): State = event match {
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

      case _ =>
        throw new IllegalArgumentException(
          s"この state(${this.getClass.getName}) では event(${event.getClass.getName}) は作成されない",
        )

    }

    @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case StopActor =>
        import lerna.util.tenant.TenantComponentLogContext.logContext
        logger.info(s"[state: $this, receive: StopActor] 処理結果待ちのため終了処理を保留します")
        Effect.stash()

      case paymentResult: SettlementResult =>
        import paymentResult.{ appRequestContext, processingContext }

        timers.cancel(processingTimeoutMessage)

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
                    Effect
                      .persist(event)
                      .thenReply(processingContext.replyTo)((_: State) => res)
                      .thenUnstashAll()
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

                    Effect
                      .persist(event)
                      .thenReply(processingContext.replyTo)((_: State) => SettlementFailureResponse(message))
                      .thenUnstashAll()
                }

              case Left(message) =>
                val event = SettlementFailureConfirmed(
                  None,
                  payCredential,
                  requestInfo,
                  req,
                  message,
                  systemTime,
                )
                Effect
                  .persist(event)
                  .thenReply(processingContext.replyTo)((_: State) => SettlementFailureResponse(message))
                  .thenUnstashAll()
            }

          // Gatewayから何のレスポンスEntity(JSON)もなし
          // 承認売上も、障害取消も
          case Left(message) =>
            val event = SettlementAborted(
              message,
              systemTime,
            )
            Effect
              .persist(event)
              .thenReply(processingContext.replyTo)((_: State) => SettlementFailureResponse(message))
              .thenUnstashAll()
        }

      case msg: Settle =>
        import msg.appRequestContext
        logger.info("前回のリクエストが処理中のため一時的に保留(stash)します")
        Effect.stash()

      case `processingTimeoutMessage` =>
        import processingTimeoutMessage.requestContext
        logger.info("処理タイムアウトしました: {}", processingTimeoutMessage)
        Effect
          .persist(SettlementTimeoutDetected()(requestContext.traceId))
          .thenRun((_: State) => stopSelfSafely())
          .thenNoReply()
          .thenUnstashAll()

      case ReceiveTimeout          => handleReceiveTimeout()
      case _: ProcessingTimeout    => Effect.unhandled.thenNoReply() // 対にならない timeout
      case _: InnerBusinessCommand => Effect.unhandled.thenNoReply()
      case _: Cancel               => Effect.stash()
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
    override def applyEvent(event: ECPaymentIssuingServiceEvent): State = event match {
      // 取消
      case event: CancelAccepted =>
        import event.traceId

        val processingTimeoutMessage: ProcessingTimeout =
          ProcessingTimeout(event.systemDateTime, askTimeout, context.system.settings.config)

        timers.startSingleTimer(
          msg = processingTimeoutMessage,
          delay = processingTimeoutMessage.timeLeft(dateTimeFactory),
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
        )

      case _ =>
        throw new IllegalArgumentException(
          s"この state(${this.getClass.getName}) では event(${event.getClass.getName}) は作成されない",
        )
    }

    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case msg: Settle =>
        import msg.appRequestContext
        msg.accept()
        logger.info("すでに処理済みのため、前回の処理結果を返します(前回とキーが同じリクエストが来ました)")

        replyAndStopSelf(msg.replyTo, settlementSuccessResponse)

      case msg: Cancel =>
        import msg.appRequestContext
        // リクエスト直前の時刻をこちらで作成（変数化）
        val systemTime = dateTimeFactory.now()

        implicit val processingContext: ProcessingContext =
          ProcessingContext(
            clientId = msg.clientId,
            walletShopId = msg.walletShopId,
            orderId = msg.orderId,
            replyTo = msg.replyTo,
          )

        Effect
          .persist(
            CancelAccepted(
              msg,
              systemTime,
            ),
          ).thenRun((_: State) => {
            msg.accept()

            val resultFuture = executeCancel(msg, customerId, originalRequestParameter, systemTime)
            context.pipeToSelf(resultFuture) { triedResult =>
              val result = triedResult.toEither.left.map(handleException)
              CancelResult(result)
            }
          }).thenNoReply()

      case StopActor               => Effect.stop().thenNoReply()
      case ReceiveTimeout          => handleReceiveTimeout()
      case _: ProcessingTimeout    => Effect.unhandled.thenNoReply()
      case _: InnerBusinessCommand => Effect.unhandled.thenNoReply()

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
  ) extends State {
    override def applyEvent(event: ECPaymentIssuingServiceEvent): State = event match {
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

      case _ =>
        throw new IllegalArgumentException(
          s"この state(${this.getClass.getName}) では event(${event.getClass.getName}) は作成されない",
        )
    }

    @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case StopActor =>
        import lerna.util.tenant.TenantComponentLogContext.logContext
        logger.info(s"[state: $this, receive: StopActor] 処理結果待ちのため終了処理を保留します")
        Effect.stash()

      case `processingTimeoutMessage` =>
        import processingTimeoutMessage.requestContext
        logger.info("処理タイムアウトしました: {}", processingTimeoutMessage)
        Effect
          .persist(
            CancelTimeoutDetected()(requestContext.traceId),
          )
          .thenRun((_: State) => stopSelfSafely())
          .thenNoReply()
          .thenUnstashAll()

      case cancelResult: CancelResult =>
        timers.cancel(processingTimeoutMessage)
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

                    Effect
                      .persist(event)
                      .thenReply(processingContext.replyTo)((_: State) => res)
                      .thenUnstashAll()

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
                    Effect
                      .persist(event)
                      .thenReply(processingContext.replyTo)((_: State) => SettlementFailureResponse(message))
                      .thenUnstashAll()

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
                    Effect
                      .persist(event)
                      .thenReply(processingContext.replyTo)((_: State) => SettlementFailureResponse(message))
                      .thenUnstashAll()
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

                Effect
                  .persist(cancelFailedEvent)
                  .thenReply(processingContext.replyTo)((_: State) => SettlementFailureResponse(message))
                  .thenUnstashAll()
            }

          case Left(message) =>
            // 非同期処理対象外
            val cancelFailedEvent =
              CancelAborted()
            Effect
              .persist(cancelFailedEvent)
              .thenReply(processingContext.replyTo)((_: State) => SettlementFailureResponse(message))
              .thenUnstashAll()
        }

      case msg: Cancel =>
        import msg.appRequestContext
        logger.info("前回のリクエストが処理中のため一時的に保留(stash)します")
        Effect.stash()

      case ReceiveTimeout          => handleReceiveTimeout()
      case _: ProcessingTimeout    => Effect.unhandled.thenNoReply() // 対にならない timeout
      case _: InnerBusinessCommand => Effect.unhandled.thenNoReply()
      case _: Settle               => Effect.stash()
    }
  }

  case class Canceled(
      cancelSuccessResponse: SettlementSuccessResponse,
      customerId: CustomerId,
      cancelResponse: IssuingServiceResponse,
  ) extends State {
    override def applyEvent(event: ECPaymentIssuingServiceEvent): State =
      throw new IllegalArgumentException(
        s"この state(${this.getClass.getName}) では event(${event.getClass.getName}) は作成されない",
      )

    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case msg: Cancel =>
        msg.accept()
        val response: SettlementResponse = if (msg.customerId === customerId) {
          import msg.appRequestContext
          logger.info("すでに処理済みのため、前回の処理結果を返します(前回とキーが同じリクエストが来ました)")
          cancelSuccessResponse
        } else {
          SettlementFailureResponse(ForbiddenFailure())
        }
        replyAndStopSelf(msg.replyTo, response)

      case StopActor               => Effect.stop().thenNoReply()
      case ReceiveTimeout          => handleReceiveTimeout()
      case _: ProcessingTimeout    => Effect.unhandled.thenNoReply()
      case _: InnerBusinessCommand => Effect.unhandled.thenNoReply()
      case command: Settle =>
        val message = ValidationFailure("walletShopId または orderId が不正です")
        replyAndStopSelf(command.replyTo, SettlementFailureResponse(message))
    }
  }

  case class Failed(
      message: OnlineProcessingFailureMessage,
  ) extends State {
    override def applyEvent(event: ECPaymentIssuingServiceEvent): State =
      throw new IllegalArgumentException(
        s"この state(${this.getClass.getName}) では event(${event.getClass.getName}) は作成されない",
      )

    override def applyCommand(cmd: Command): ReplyEffect = cmd match {
      case msg: Settle =>
        import msg.appRequestContext
        msg.accept()
        logger.info("すでに処理済みのため、前回の処理結果を返します(前回とキーが同じリクエストが来ました)")

        logger.info(
          s"status: failed, msg: $msg",
        )
        replyAndStopSelf(msg.replyTo, SettlementFailureResponse(message))

      case StopActor               => Effect.stop().thenNoReply()
      case ReceiveTimeout          => handleReceiveTimeout()
      case _: ProcessingTimeout    => Effect.unhandled.thenNoReply()
      case _: InnerBusinessCommand => Effect.unhandled.thenNoReply()
      case command: Cancel =>
        val message = ValidationFailure("walletShopId または orderId が不正です")
        replyAndStopSelf(command.replyTo, SettlementFailureResponse(message))
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

  private def handleReceiveTimeout(): ReplyEffect = {
    implicit val appRequestContext: AppRequestContext = AppRequestContext(TraceId.unknown, tenant)
    logger.info("Actorの生成から一定時間経過しました。Actorを停止します。")
    stopSelfSafely()
    Effect.noReply
  }

  // 返事およびアウターストップ
  private def replyAndStopSelf(replyTo: ActorRef[SettlementResponse], msg: SettlementResponse): ReplyEffect = {
    Effect.none
      .thenRun((_: State) => stopSelfSafely())
      .thenReply(replyTo)(_ => msg)
  }

  private def stopSelfSafely(): Unit = {
    entityContext.shard ! ClusterSharding.Passivate(context.self)
  }
}
