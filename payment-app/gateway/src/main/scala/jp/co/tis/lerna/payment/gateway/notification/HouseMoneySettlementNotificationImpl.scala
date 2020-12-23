package jp.co.tis.lerna.payment.gateway.notification

import akka.actor.{ ActorSystem, Scheduler }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ `Cache-Control`, Accept, CacheDirectives }
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.notification.HouseMoneySettlementNotification
import jp.co.tis.lerna.payment.adapter.notification.util.model._
import jp.co.tis.lerna.payment.adapter.util.PayCompleteNotifyBadRequest
import jp.co.tis.lerna.payment.utility.AppRequestContext
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.http.HttpRequestLoggingSupport
import lerna.log.AppLogging
import lerna.util.time.JavaDurationConverters._
import spray.json.RootJsonFormat

import scala.collection.immutable
import scala.concurrent.Future

object HouseMoneySettlementNotificationImpl extends SprayJsonSupport {
  import spray.json.DefaultJsonProtocol._
  implicit val notificationRequestJsonFormat: RootJsonFormat[HouseMoneySettlementNotificationRequest] = jsonFormat1(
    HouseMoneySettlementNotificationRequest,
  )
  implicit val errorsJsonFormat: RootJsonFormat[Errors] = jsonFormat3(Errors)
  implicit val notificationErrorResponseJsonFormat: RootJsonFormat[NotificationErrorResponse] = jsonFormat2(
    NotificationErrorResponse,
  )
}

final case class HouseMoneySettlementNotificationImpl(rootConfig: Config, implicit val system: ActorSystem)
    extends HouseMoneySettlementNotification
    with HttpRequestLoggingSupport
    with AppLogging {
  import HouseMoneySettlementNotificationImpl._
  import system.dispatcher
  implicit val scheduler: Scheduler = system.scheduler

  private val config                              = rootConfig.getConfig("jp.co.tis.lerna.payment.gateway.wallet-system")
  private def baseUrl(implicit tenant: AppTenant) = config.getString(s"tenants.${tenant.id}.base-url")
  private def timeOut(implicit tenant: AppTenant) = config.getDuration(s"tenants.${tenant.id}.response-timeout").asScala

  override def notice(
      walletSettlementId: String,
  )(implicit appRequestContext: AppRequestContext): Future[NotificationResponse] = {
    implicit val tenant: AppTenant = appRequestContext.tenant

    val request = HouseMoneySettlementNotificationRequest(walletSettlementId)
    val requestHeader = immutable.Seq[HttpHeader](
      Accept(MediaTypes.`application/json`),
      `Cache-Control`(CacheDirectives.`no-cache`),
    )

    val res = for {
      requestEntity <- Marshal(request).to[RequestEntity]
      response <- httpSingleRequestWithAroundLogWithTimeout(
        HttpRequest
          .apply(
            method = HttpMethods.POST,
            uri = Uri(baseUrl + "/housemoney/settlement/notices"),
            entity = requestEntity,
            headers = requestHeader,
          ),
        timeOut,
      )
      entity <- {
        response.status match {
          case StatusCodes.OK =>
            response.discardEntityBytes()
            Future.successful[NotificationResponse](NotificationSuccess())
          case StatusCodes.BadRequest =>
            Unmarshal(response.entity).to[NotificationErrorResponse].map { res =>
              logger.error(s"response Status Code: ${response.status.value}, code: ${res.errors
                .getOrElse(Errors("", "", "empty errors").code)}, message: ${res.message.getOrElse("empty message")}")

              val message = PayCompleteNotifyBadRequest("ハウスマネー決済完通知API呼び出し", res.message.getOrElse("-"))
              logger.warn(s"${message.messageId}: ${message.messageContent}")
              NotificationFailure()
            }
          case _ =>
            response.discardEntityBytes()
            Future.successful[NotificationResponse](NotificationFailure())
        }
      }
    } yield entity

    res.recover {
      case ex =>
        logger.warn(ex, ex.getMessage)
        NotificationFailure()
    }
  }
}
