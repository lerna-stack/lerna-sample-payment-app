package jp.co.tis.lerna.payment.application.ecpayment.issuing.actor

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ typed, ActorSystem }
import akka.cluster.sharding.typed.scaladsl.{ EntityContext, EntityTypeKey }
import akka.testkit.{ ImplicitSender, TestKit, TestProbe }
import com.typesafe.config.{ Config, ConfigFactory }
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model._
import jp.co.tis.lerna.payment.adapter.ecpayment.model.{ OrderId, WalletShopId }
import jp.co.tis.lerna.payment.adapter.issuing.IssuingServiceGateway
import jp.co.tis.lerna.payment.adapter.issuing.model.{
  AcquirerReversalRequestParameter,
  AuthorizationRequestParameter,
  IssuingServiceResponse,
}
import jp.co.tis.lerna.payment.adapter.util._
import jp.co.tis.lerna.payment.adapter.util.exception.BusinessException
import jp.co.tis.lerna.payment.adapter.wallet.{ ClientId, CustomerId }
import jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.PaymentActor.withLogger
import jp.co.tis.lerna.payment.application.ecpayment.issuing.{ PaymentIdFactory, TransactionIdFactory }
import jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantShardingSupportTestHelper
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.readmodel.{ JDBCService, JDBCSupport, ReadModelDIDesign }
import jp.co.tis.lerna.payment.utility.AppRequestContext
import lerna.testkit.airframe.DISessionSupport
import lerna.util.akka.AtLeastOnceDelivery
import lerna.util.tenant.Tenant
import lerna.util.time.{ FixedLocalDateTimeFactory, LocalDateTimeFactory }
import lerna.util.trace.TraceId
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.Inside
import wvlet.airframe.Design

import scala.concurrent.Future
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

// Lint回避のため
@SuppressWarnings(
  Array(
    "lerna.warts.Awaits",
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Throw",
    "org.wartremover.warts.ExplicitImplicitTypes",
  ),
)
class PaymentActorSpec
    extends TestKit(ActorSystem("PaymentActorSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with DISessionSupport
    with JDBCSupport
    with Inside
    with ScalaFutures {
  import tableSeeds._
  import tables._
  import tables.profile.api._

  override protected val diDesign: Design = ReadModelDIDesign.readModelDesign
    .add(ReadModelDIDesign.readModelDesign)
    .bind[ActorSystem].toInstance(system)
    .bind[Config].toInstance(ConfigFactory.load)
    .bind[LocalDateTimeFactory].toInstance(FixedLocalDateTimeFactory("2019-05-01T00:00:00Z"))
    .bind[PaymentActor].toSingleton

  implicit val appRequestContext: AppRequestContext = AppRequestContext(TraceId("1"), tenant)

  val paymentIdFactory: PaymentIdFactory = new PaymentIdFactory {
    override def generateIdFor(customerId: CustomerId)(implicit tenant: Tenant): Future[PaymentId] = {
      Future.successful(PaymentId(12345))
    }
  }

  val transactionIdFactorySuccess: TransactionIdFactory = new TransactionIdFactory {
    override def generate()(implicit tenant: Tenant): Future[TransactionId] = {
      Future.successful(TransactionId(123456789012L))
    }
  }

  private val replyTo                                          = testActor.toTyped
  private implicit val typedSystem: typed.ActorSystem[Nothing] = system.toTyped
  private val typedTestkit                                     = ActorTestKit(typedSystem)

  val dateTime: LocalDateTimeFactory = diSession.build[LocalDateTimeFactory]

  "ハウスマニーアクター" when {
    implicit val database: JDBCService = diSession.build[JDBCService]
    implicit val tables: Tables        = diSession.build[Tables]

    def generateResponse(errCode: String = "00000") = {
      IssuingServiceResponse(
        IntranId("1234567890ABCDEFG"), //intranid: String,
        "123456",                      //authId: String,
        errCode,                       //rErrcode: String,
      )
    }

    val issuingServiceGatewaySuccess = new IssuingServiceGateway() {
      override def requestAuthorization(
          parameter: AuthorizationRequestParameter,
      )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
        Future.successful(generateResponse())

      override def requestAcquirerReversal(
          parameter: AcquirerReversalRequestParameter,
          originalRequestParameter: AuthorizationRequestParameter,
      )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
        Future.successful(generateResponse())
    }

    "初期状態" when {
      "決済要求が送信された" when {
        "DBに合うデータがない" should {
          "レスポンス：NotFound" in withJDBC { db =>
            db.prepare(
              Customer += CustomerRowSeed.copy(
                customerId = "123",
                customerNumber = Option("5.55"),
                walletId = Option("123"),
              ),
              HouseMemberStore += HouseMemberStoreRowSeed.copy(
                memberStoreId = "123",
                memberStoreNameJp = Option("456"),
                memberStoreNameEn = Option("abc"),
                terminalId = "l23",
                clientId = "123",
              ),
              ServiceRelation += ServiceRelationRowSeed
                .copy(serviceRelationId = BigDecimal(1.11), customerId = "123", foreignKeyType = "00"),
              IssuingService += IssuingServiceRowSeed.copy(
                contractNumber = "2345",
                housePan = Option("456"),
                serviceRelationId = Option(BigDecimal(1.11)),
              ),
            )

            val payRequest =
              Settle(
                ClientId(5),
                CustomerId("999"),
                WalletShopId("123"),
                OrderId("456"),
                AmountTran(5),
                replyTo,
                _,
              )
            val actor = createActor(issuingServiceGatewaySuccess)

            AtLeastOnceDelivery.tellTo(actor, payRequest)

            val expect = NotFound("決済情報")
            expectMsg(SettlementFailureResponse(expect))
          }
        }

        "housePanが設定されていない(NULL可能)" should {
          "レスポンス：NotFound" in withJDBC { db =>
            db.prepare(
              Customer += CustomerRowSeed.copy(
                customerId = "789",
                customerNumber = Option("5.55"),
                walletId = Option("1214"),
              ),
              HouseMemberStore += HouseMemberStoreRowSeed.copy(
                memberStoreId = "123",
                memberStoreNameJp = Option("456"),
                memberStoreNameEn = Option("abc"),
                terminalId = "12",
                clientId = "777",
              ),
              ServiceRelation += ServiceRelationRowSeed
                .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
              IssuingService += IssuingServiceRowSeed.copy(
                contractNumber = "2345",
                housePan = None, // !!!
                serviceRelationId = Option(BigDecimal(1.11)),
              ),
            )
            val payRequest =
              Settle(
                ClientId(777),
                CustomerId("789"),
                WalletShopId("123"),
                OrderId("456"),
                AmountTran(5),
                replyTo,
                _,
              )
            val actor = createActor(issuingServiceGatewaySuccess)

            AtLeastOnceDelivery.tellTo(actor, payRequest)
            val expect = NotFound("決済情報")
            expectMsg(SettlementFailureResponse(expect))
          }
        }

        "Gateway レスポンス：正常" should {
          "エラーコード：正常(00000)" should {
            "決済成功" in withJDBC { db =>
              db.prepare(
                Customer += CustomerRowSeed.copy(
                  customerId = "789",
                  customerNumber = Option("5.55"),
                  walletId = Option("123"),
                ),
                HouseMemberStore += HouseMemberStoreRowSeed.copy(
                  walletShopId = "123",
                  memberStoreNameJp = Option("456"),
                  memberStoreNameEn = Option("abc"),
                  terminalId = "12",
                  clientId = "777",
                ),
                ServiceRelation += ServiceRelationRowSeed
                  .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
                IssuingService += IssuingServiceRowSeed.copy(
                  contractNumber = "2345",
                  housePan = Option("132"),
                  serviceRelationId = Option(BigDecimal(1.11)),
                ),
              )

              val payRequest =
                Settle(
                  ClientId(777),
                  CustomerId("789"),
                  WalletShopId("123"),
                  OrderId("57"),
                  AmountTran(5),
                  replyTo,
                  _,
                )
              val actor = createActor(issuingServiceGatewaySuccess)

              AtLeastOnceDelivery.tellTo(actor, payRequest)
              expectMsg(SettlementSuccessResponse())
            }
          }

          "決済失敗 エラーコード：異常(00000以外) -> 取消送信(ValidationFailureエラー返却)" when {
            "レスポンス：エラーメッセージ" in withJDBC { db =>
              db.prepare(
                Customer += CustomerRowSeed.copy(
                  customerId = "789",
                  customerNumber = Option("5.55"),
                  walletId = Option("123"),
                ),
                HouseMemberStore += HouseMemberStoreRowSeed.copy(
                  walletShopId = "123",
                  memberStoreNameJp = Option("456"),
                  memberStoreNameEn = Option("abc"),
                  terminalId = "12",
                  clientId = "777",
                ),
                ServiceRelation += ServiceRelationRowSeed
                  .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
                IssuingService += IssuingServiceRowSeed.copy(
                  contractNumber = "2345",
                  housePan = Option("132"),
                  serviceRelationId = Option(BigDecimal(1.11)),
                ),
              )
              val issuingServiceGatewayErrCode = new IssuingServiceGateway() {
                override def requestAuthorization(parameter: AuthorizationRequestParameter)(implicit
                    appRequestContext: AppRequestContext,
                ): Future[IssuingServiceResponse] = Future.successful(generateResponse("TW001"))

                override def requestAcquirerReversal(
                    parameter: AcquirerReversalRequestParameter,
                    originalRequestParameter: AuthorizationRequestParameter,
                )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
                  Future.successful(generateResponse("TW001"))
              }
              val payRequest =
                Settle(
                  ClientId(777),
                  CustomerId("789"),
                  WalletShopId("123"),
                  OrderId("567"),
                  AmountTran(5),
                  replyTo,
                  _,
                )
              val actor = createActor(issuingServiceGatewayErrCode)

              AtLeastOnceDelivery.tellTo(actor, payRequest)
              val expect = IssuingServiceServerError("承認売上送信", "TW001")
              expectMsg(SettlementFailureResponse(expect))

              val cancelRequest =
                Cancel(ClientId(777), CustomerId("789"), WalletShopId("123"), OrderId("456"), replyTo, _)
              AtLeastOnceDelivery.tellTo(actor, cancelRequest)
              val expectedClientErrorMessage = ValidationFailure("walletShopId または orderId が不正です")

              expectMsg(SettlementFailureResponse(expectedClientErrorMessage))
            }
          }
        }

        "Gateway Failure" in withJDBC { db =>
          db.prepare(
            Customer += CustomerRowSeed.copy(
              customerId = "789",
              customerNumber = Option("5.55"),
              walletId = Option("123"),
            ),
            HouseMemberStore += HouseMemberStoreRowSeed.copy(
              walletShopId = "123",
              memberStoreNameJp = Option("456"),
              memberStoreNameEn = Option("abc"),
              terminalId = "12",
              clientId = "777",
            ),
            ServiceRelation += ServiceRelationRowSeed
              .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
            IssuingService += IssuingServiceRowSeed.copy(
              contractNumber = "2345",
              housePan = Option("132"),
              serviceRelationId = Option(BigDecimal(1.11)),
            ),
          )
          val issuingServiceGatewayFail = new IssuingServiceGateway() {
            override def requestAuthorization(
                parameter: AuthorizationRequestParameter,
            )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
              Future.failed(new BusinessException(TimeOut("Some error")))

            override def requestAcquirerReversal(
                parameter: AcquirerReversalRequestParameter,
                originalRequestParameter: AuthorizationRequestParameter,
            )(implicit
                appRequestContext: AppRequestContext,
            ): Future[IssuingServiceResponse] = ???
          }
          val payRequest =
            Settle(
              ClientId(777),
              CustomerId("789"),
              WalletShopId("123"),
              OrderId("456"),
              AmountTran(5),
              replyTo,
              _,
            )
          val actor = createActor(issuingServiceGatewayFail)

          AtLeastOnceDelivery.tellTo(actor, payRequest)

          val expect = TimeOut("Some error")
          expectMsg(SettlementFailureResponse(expect))
        }

        "決済失敗: 未知の異常" in withJDBC { db =>
          db.prepare(
            Customer += CustomerRowSeed.copy(
              customerId = "789",
              customerNumber = Option("5.55"),
              walletId = Option("123"),
            ),
            HouseMemberStore += HouseMemberStoreRowSeed.copy(
              walletShopId = "123",
              memberStoreNameJp = Option("456"),
              memberStoreNameEn = Option("abc"),
              terminalId = "12",
              clientId = "777",
            ),
            ServiceRelation += ServiceRelationRowSeed
              .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
            IssuingService += IssuingServiceRowSeed.copy(
              contractNumber = "2345",
              housePan = Option("132"),
              serviceRelationId = Option(BigDecimal(1.11)),
            ),
          )
          val payRequest =
            Settle(ClientId(777), CustomerId("789"), WalletShopId("123"), OrderId("57"), AmountTran(5), replyTo, _)
          val transactionIdFactory: TransactionIdFactory = new TransactionIdFactory {
            override def generate()(implicit tenant: Tenant): Future[TransactionId] = {
              Future.failed(new RuntimeException)
            }
          }
          val actor = createActor(issuingServiceGatewaySuccess, transactionIdFactory)

          AtLeastOnceDelivery.tellTo(actor, payRequest)
          val expect = UnpredictableError()
          expectMsg(SettlementFailureResponse(expect))
        }
      }
    }

    "決済成功の状態" when {
      "決済要求が再度送信" when {
        "同じレスポンスが返される" in withJDBC { db =>
          db.prepare(
            Customer += CustomerRowSeed.copy(
              customerId = "789",
              customerNumber = Option("5.55"),
              walletId = Option("123"),
            ),
            HouseMemberStore += HouseMemberStoreRowSeed.copy(
              walletShopId = "123",
              memberStoreNameJp = Option("456"),
              memberStoreNameEn = Option("abc"),
              terminalId = "12",
              clientId = "777",
            ),
            ServiceRelation += ServiceRelationRowSeed
              .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
            IssuingService += IssuingServiceRowSeed.copy(
              contractNumber = "2345",
              housePan = Option("132"),
              serviceRelationId = Option(BigDecimal(1.11)),
            ),
          )

          val payRequest =
            Settle(
              ClientId(777),
              CustomerId("789"),
              WalletShopId("123"),
              OrderId("5555"),
              AmountTran(5),
              replyTo,
              _,
            )
          val actor = createActor(issuingServiceGatewaySuccess)

          AtLeastOnceDelivery.tellTo(actor, payRequest)
          expectMsg(SettlementSuccessResponse())

          AtLeastOnceDelivery.tellTo(actor, payRequest)
          expectMsg(SettlementSuccessResponse())
        }
      }

      "決済取消送信、GatewayレスポンスがNormal かつ エラーコードが正常(00000) → 取消成功 → 決済送信" in withJDBC { db =>
        db.prepare(
          Customer += CustomerRowSeed.copy(
            customerId = "789",
            customerNumber = Option("5.55"),
            walletId = Option("123"),
          ),
          HouseMemberStore += HouseMemberStoreRowSeed.copy(
            walletShopId = "123",
            memberStoreNameJp = Option("456"),
            memberStoreNameEn = Option("wallet shop name"),
            terminalId = "1234567890ABC",
            clientId = "777",
          ),
          ServiceRelation += ServiceRelationRowSeed
            .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
          IssuingService += IssuingServiceRowSeed.copy(
            contractNumber = "2345",
            housePan = Option("1234567890123456"),
            serviceRelationId = Option(BigDecimal(1.11)),
          ),
        )

        val payRequest = Settle(
          ClientId(777),
          CustomerId("789"),
          WalletShopId("123"),
          OrderId("789"),
          AmountTran(5),
          replyTo,
          _,
        )
        val actor = createActor(issuingServiceGatewaySuccess)

        AtLeastOnceDelivery.tellTo(actor, payRequest)
        expectMsg(SettlementSuccessResponse())

        // 加盟店名称、端末番号を取得しなおす
        db.prepare(
          HouseMemberStore insertOrUpdate HouseMemberStoreRowSeed.copy(
            memberStoreId = "123",
            memberStoreNameJp = Option("456"),
            memberStoreNameEn = Option("wallet shop name 2"), //!!!
            terminalId = "ABC1234567890",                     //!!!
            clientId = "777",
          ),
        )

        val cancelRequest =
          Cancel(ClientId(777), CustomerId("789"), WalletShopId("123"), OrderId("456"), replyTo, _)
        AtLeastOnceDelivery.tellTo(actor, cancelRequest)
        expectMsg(SettlementSuccessResponse())

        AtLeastOnceDelivery.tellTo(actor, payRequest)
        val expectedClientErrorMessage = ValidationFailure("walletShopId または orderId が不正です")
        expectMsg(SettlementFailureResponse(expectedClientErrorMessage))
      }

      "決済取消送信、DBに合うデータがない、永続化しない" in withJDBC { db =>
        db.prepare(
          Customer += CustomerRowSeed.copy(
            customerId = "789",
            customerNumber = Option("5.55"),
            walletId = Option("123"),
          ),
          HouseMemberStore += HouseMemberStoreRowSeed.copy(
            walletShopId = "123",
            memberStoreNameJp = Option("456"),
            memberStoreNameEn = Option("wallet shop name"),
            terminalId = "12",
            clientId = "777",
          ),
          ServiceRelation += ServiceRelationRowSeed
            .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
          IssuingService += IssuingServiceRowSeed.copy(
            contractNumber = "2345",
            housePan = Option("1234567890123456"),
            serviceRelationId = Option(BigDecimal(1.11)),
          ),
        )

        val payRequest = Settle(
          ClientId(777),
          CustomerId("789"),
          WalletShopId("123"),
          OrderId("789"),
          AmountTran(5),
          replyTo,
          _,
        )
        val actor = createActor(issuingServiceGatewaySuccess)

        AtLeastOnceDelivery.tellTo(actor, payRequest)
        expectMsg(SettlementSuccessResponse())

        // データクリア
        db.prepare(
          HouseMemberStore.delete,
        )

        val cancelRequest =
          Cancel(ClientId(777), CustomerId("789"), WalletShopId("123"), OrderId("456"), replyTo, _)
        AtLeastOnceDelivery.tellTo(actor, cancelRequest)

        val expect = NotFound("決済情報")
        expectMsg(SettlementFailureResponse(expect))
      }

      "決済取消送信、GatewayレスポンスがNormal かつ エラーコードが「既に取消済み」 → 取消失敗(IssuingServiceAlreadyCanceled)" in withJDBC { db =>
        db.prepare(
          Customer += CustomerRowSeed.copy(
            customerId = "789",
            customerNumber = Option("5.55"),
            walletId = Option("123"),
          ),
          HouseMemberStore += HouseMemberStoreRowSeed.copy(
            walletShopId = "123",
            memberStoreNameJp = Option("456"),
            memberStoreNameEn = Option("abc"),
            terminalId = "12",
            clientId = "777",
          ),
          ServiceRelation += ServiceRelationRowSeed
            .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
          IssuingService += IssuingServiceRowSeed.copy(
            contractNumber = "2345",
            housePan = Option("132"),
            serviceRelationId = Option(BigDecimal(1.11)),
          ),
        )
        val issuingServiceGatewayOKNG = new IssuingServiceGateway() {
          override def requestAuthorization(
              parameter: AuthorizationRequestParameter,
          )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
            Future.successful(generateResponse())

          override def requestAcquirerReversal(
              parameter: AcquirerReversalRequestParameter,
              originalRequestParameter: AuthorizationRequestParameter,
          )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
            Future.successful(generateResponse("TW005"))
        }
        val payRequest =
          Settle(
            ClientId(777),
            CustomerId("789"),
            WalletShopId("123"),
            OrderId("7777"),
            AmountTran(5),
            replyTo,
            _,
          )
        val actor = createActor(issuingServiceGatewayOKNG)

        AtLeastOnceDelivery.tellTo(actor, payRequest)
        expectMsg(SettlementSuccessResponse())

        val cancelRequest =
          Cancel(ClientId(777), CustomerId("789"), WalletShopId("123"), OrderId("456"), replyTo, _)
        AtLeastOnceDelivery.tellTo(actor, cancelRequest)
        // CODE-011
        val expect = IssuingServiceAlreadyCanceled()
        expectMsg(SettlementFailureResponse(expect))
      }

      "決済取消送信、GatewayレスポンスがNormal かつ エラーコードが正常、「既に取消済み」以外 → 取消失敗(IssuingServiceServerError" in withJDBC { db =>
        db.prepare(
          Customer += CustomerRowSeed.copy(
            customerId = "789",
            customerNumber = Option("5.55"),
            walletId = Option("123"),
          ),
          HouseMemberStore += HouseMemberStoreRowSeed.copy(
            walletShopId = "123",
            memberStoreNameJp = Option("456"),
            memberStoreNameEn = Option("abc"),
            terminalId = "12",
            clientId = "777",
          ),
          ServiceRelation += ServiceRelationRowSeed
            .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
          IssuingService += IssuingServiceRowSeed.copy(
            contractNumber = "2345",
            housePan = Option("132"),
            serviceRelationId = Option(BigDecimal(1.11)),
          ),
        )
        val issuingServiceGatewayOKNG = new IssuingServiceGateway() {
          override def requestAuthorization(
              parameter: AuthorizationRequestParameter,
          )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
            Future.successful(generateResponse())

          override def requestAcquirerReversal(
              parameter: AcquirerReversalRequestParameter,
              originalRequestParameter: AuthorizationRequestParameter,
          )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
            Future.successful(generateResponse("TW900"))
        }
        val payRequest = Settle(
          ClientId(777),
          CustomerId("789"),
          WalletShopId("123"),
          OrderId("456"),
          AmountTran(5),
          replyTo,
          _,
        )
        val actor = createActor(issuingServiceGatewayOKNG)

        AtLeastOnceDelivery.tellTo(actor, payRequest)
        expectMsg(SettlementSuccessResponse())

        val cancelRequest =
          Cancel(ClientId(777), CustomerId("789"), WalletShopId("123"), OrderId("456"), replyTo, _)
        AtLeastOnceDelivery.tellTo(actor, cancelRequest)
        val expect = IssuingServiceServerError("承認取消送信", "TW900")
        expectMsg(SettlementFailureResponse(expect))
      }

      "決済取消送信、GatewayレスポンスがFailure → エラー" in withJDBC { db =>
        db.prepare(
          Customer += CustomerRowSeed.copy(
            customerId = "789",
            customerNumber = Option("5.55"),
            walletId = Option("123"),
          ),
          HouseMemberStore += HouseMemberStoreRowSeed.copy(
            walletShopId = "123",
            memberStoreNameJp = Option("456"),
            memberStoreNameEn = Option("abc"),
            terminalId = "12",
            clientId = "777",
          ),
          ServiceRelation += ServiceRelationRowSeed
            .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
          IssuingService += IssuingServiceRowSeed.copy(
            contractNumber = "2345",
            housePan = Option("132"),
            serviceRelationId = Option(BigDecimal(1.11)),
          ),
        )
        val issuingServiceGatewayOKFail = new IssuingServiceGateway() {
          override def requestAuthorization(
              parameter: AuthorizationRequestParameter,
          )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
            Future.successful(generateResponse())

          override def requestAcquirerReversal(
              parameter: AcquirerReversalRequestParameter,
              originalRequestParameter: AuthorizationRequestParameter,
          )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
            Future.failed(new BusinessException(TimeOut("123")))
        }
        val payRequest = Settle(
          ClientId(777),
          CustomerId("789"),
          WalletShopId("123"),
          OrderId("456"),
          AmountTran(5),
          replyTo,
          _,
        )
        val actor = createActor(issuingServiceGatewayOKFail)

        AtLeastOnceDelivery.tellTo(actor, payRequest)
        expectMsg(SettlementSuccessResponse())

        val cancelRequest =
          Cancel(ClientId(777), CustomerId("789"), WalletShopId("123"), OrderId("456"), replyTo, _)
        AtLeastOnceDelivery.tellTo(actor, cancelRequest)

        val expect = TimeOut("123")
        expectMsg(SettlementFailureResponse(expect))
      }

      "取消成功、再度送信 → 取消成功" in withJDBC { db =>
        db.prepare(
          Customer += CustomerRowSeed.copy(
            customerId = "789",
            customerNumber = Option("5.55"),
            walletId = Option("123"),
          ),
          HouseMemberStore += HouseMemberStoreRowSeed.copy(
            walletShopId = "123",
            memberStoreNameJp = Option("456"),
            memberStoreNameEn = Option("abc"),
            terminalId = "12",
            clientId = "777",
          ),
          ServiceRelation += ServiceRelationRowSeed
            .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
          IssuingService += IssuingServiceRowSeed.copy(
            contractNumber = "2345",
            housePan = Option("132"),
            serviceRelationId = Option(BigDecimal(1.11)),
          ),
        )
        val payRequest = Settle(
          ClientId(777),
          CustomerId("789"),
          WalletShopId("123"),
          OrderId("456"),
          AmountTran(5),
          replyTo,
          _,
        )
        val actor = createActor(issuingServiceGatewaySuccess)

        AtLeastOnceDelivery.tellTo(actor, payRequest)
        expectMsg(SettlementSuccessResponse())

        val cancelRequest =
          Cancel(ClientId(777), CustomerId("789"), WalletShopId("123"), OrderId("456"), replyTo, _)
        AtLeastOnceDelivery.tellTo(actor, cancelRequest)
        AtLeastOnceDelivery.tellTo(actor, cancelRequest)
        expectMsg(SettlementSuccessResponse())

        expectMsg(SettlementSuccessResponse())
      }

      "取消失敗、再度送信 → 取消失敗" in withJDBC { db =>
        db.prepare(
          Customer += CustomerRowSeed.copy(
            customerId = "789",
            customerNumber = Option("5.55"),
            walletId = Option("123"),
          ),
          HouseMemberStore += HouseMemberStoreRowSeed.copy(
            walletShopId = "123",
            memberStoreNameJp = Option("456"),
            memberStoreNameEn = Option("abc"),
            terminalId = "12",
            clientId = "777",
          ),
          ServiceRelation += ServiceRelationRowSeed
            .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
          IssuingService += IssuingServiceRowSeed.copy(
            contractNumber = "2345",
            housePan = Option("132"),
            serviceRelationId = Option(BigDecimal(1.11)),
          ),
        )

        val gatewayException = new BusinessException(NotFound("123"))

        val issuingServiceGatewayOKFail = new IssuingServiceGateway() {
          override def requestAuthorization(
              parameter: AuthorizationRequestParameter,
          )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
            Future.successful(generateResponse())

          override def requestAcquirerReversal(
              parameter: AcquirerReversalRequestParameter,
              originalRequestParameter: AuthorizationRequestParameter,
          )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] =
            Future.failed(gatewayException)
        }
        val payRequest = Settle(
          ClientId(777),
          CustomerId("789"),
          WalletShopId("123"),
          OrderId("456"),
          AmountTran(5),
          replyTo,
          _,
        )
        val actor = createActor(issuingServiceGatewayOKFail)

        AtLeastOnceDelivery.tellTo(actor, payRequest)
        expectMsg(SettlementSuccessResponse())

        val cancelRequest =
          Cancel(ClientId(777), CustomerId("789"), WalletShopId("123"), OrderId("456"), replyTo, _)
        AtLeastOnceDelivery.tellTo(actor, cancelRequest)
        AtLeastOnceDelivery.tellTo(actor, cancelRequest)

        val expectedMessage = gatewayException.message
        expectMsg(SettlementFailureResponse(expectedMessage))
        expectMsg(SettlementFailureResponse(expectedMessage))
      }
    }

    "決済失敗の状態" when {
      "決済要求再度送信" when {
        "同じエラーメッセージが返される" in withJDBC { db =>
          db.prepare(
            Customer += CustomerRowSeed.copy(
              customerId = "789",
              customerNumber = Option("5.55"),
              walletId = Option("123"),
            ),
            HouseMemberStore += HouseMemberStoreRowSeed.copy(
              walletShopId = "123",
              memberStoreNameJp = Option("456"),
              memberStoreNameEn = Option("abc"),
              terminalId = "77",
              clientId = "777",
            ),
            ServiceRelation += ServiceRelationRowSeed
              .copy(serviceRelationId = BigDecimal(1.11), customerId = "789", foreignKeyType = "00"),
            IssuingService += IssuingServiceRowSeed.copy(
              contractNumber = "2345",
              housePan = Option("132"),
              serviceRelationId = Option(BigDecimal(1.11)),
            ),
          )

          val expect = IssuingServiceServerError("承認売上送信")

          val issuingServiceGatewayFailure = new IssuingServiceGateway() {
            override def requestAuthorization(
                parameter: AuthorizationRequestParameter,
            )(implicit appRequestContext: AppRequestContext): Future[IssuingServiceResponse] = {
              throw new BusinessException(expect)
            }

            override def requestAcquirerReversal(
                parameter: AcquirerReversalRequestParameter,
                originalRequestParameter: AuthorizationRequestParameter,
            )(implicit
                appRequestContext: AppRequestContext,
            ): Future[IssuingServiceResponse] = ???
          }
          val payRequest =
            Settle(
              ClientId(777),
              CustomerId("789"),
              WalletShopId("123"),
              OrderId("567"),
              AmountTran(5),
              replyTo,
              _,
            )
          val actor = createActor(issuingServiceGatewayFailure)

          AtLeastOnceDelivery.tellTo(actor, payRequest)
          AtLeastOnceDelivery.tellTo(actor, payRequest)

          expectMsg(SettlementFailureResponse(expect))
          expectMsg(SettlementFailureResponse(expect))
        }
      }
    }
  }

  def createActor(
      gateway: IssuingServiceGateway,
      transactionIdFactory: TransactionIdFactory = transactionIdFactorySuccess,
  )(implicit jdbcService: JDBCService, tables: Tables): ActorRef[Command] =
    typedTestkit.spawn(
      Behaviors.setup[Command](context => {
        Behaviors.withTimers(timers => {
          withLogger(logger => {
            val entityContext = new EntityContext[Command](
              EntityTypeKey("dummy"),
              entityId = MultiTenantShardingSupportTestHelper.generateEntityId(),
              shard = TestProbe().ref.toTyped,
            )
            implicit val setup: PaymentActor.Setup = PaymentActor.Setup(
              gateway,
              jdbcService,
              tables,
              dateTime,
              transactionIdFactory,
              paymentIdFactory,
              context,
              timers,
              entityContext,
              logger,
            )
            val actor = new PaymentActor()
            actor.eventSourcedBehavior()
          })
        })
      }),
    )

  override def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }
}
