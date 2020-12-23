package jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail

import java.sql.Timestamp
import java.util.UUID

import akka.actor.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{ EventEnvelope, Offset, PersistenceQuery, TimeBasedUUID }
import akka.stream.scaladsl.{ Flow, Keep, RunnableGraph, Sink }
import akka.stream.{ KillSwitch, KillSwitches }
import akka.{ Done, NotUsed }
import com.datastax.driver.core.utils.UUIDs
import jp.co.tis.lerna.payment.adapter.notification.util.model.{
  NotificationRequest,
  NotificationResponse,
  NotificationSuccess,
}
import jp.co.tis.lerna.payment.application.readmodelupdater.ReadModelUpdater
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.model.{
  MultipleResultTransaction,
  SalesDetailDomainEvent,
  SingleResultTransaction,
}
import jp.co.tis.lerna.payment.readmodel.JDBCService
import jp.co.tis.lerna.payment.readmodel.constant.{ LogicalDeleteFlag, SystemIdentify }
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.utility.AppRequestContext
import kamon.metric.MeasurementUnit
import kamon.{ Kamon, Tags }
import lerna.log.AppLogging
import lerna.util.akka.stream.FailureSkipFlow
import lerna.util.time.LocalDateTimeFactory
import lerna.util.trace.TraceId

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait SalesDetailReadModelUpdater extends ReadModelUpdater with HasSalesDetailDomainEventTag with AppLogging {

  val jdbcService: JDBCService
  val tables: Tables
  val dateTimeFactory: LocalDateTimeFactory

  import tables._
  import tables.profile.api._

  protected implicit def appRequestContext(implicit traceId: TraceId): AppRequestContext =
    AppRequestContext(traceId, tenant)

  override def generateReadModelUpdaterStream(
      system: ActorSystem,
  ): Future[RunnableGraph[(KillSwitch, Future[Done])]] = {
    import lerna.util.tenant.TenantComponentLogContext.logContext
    import system.dispatcher

    val readJournal: CassandraReadJournal =
      PersistenceQuery(system)
        .readJournalFor(s"jp.co.tis.lerna.payment.application.persistence.cassandra.tenants.${tenant.id}.query")

    fetchOffsetUUID() map { savedOffsetUUID =>
      val offset = savedOffsetUUID.fold(Offset.noOffset) { uuidString =>
        val uuid = UUID.fromString(uuidString)
        Offset.timeBasedUUID(uuid)
      }

      logger.info("starting get events by tag({}) from offset({})", domainEventTag, offset)

      readJournal
        .eventsByTag(domainEventTag, offset)
        .viaMat(KillSwitches.single)(Keep.right)
        .via(updateReadModelFlow)
        .via(noticeResultFlow)
        .toMat(Sink.ignore)(Keep.both)
    }
  }

  /** すでにEventが一度処理されているかチェックする
    * @param eventEnvelope EventEnvelope
    * @return 処理済みかどうか
    */
  private[this] def checkAlreadyUpdated(eventEnvelope: EventEnvelope): DBIO[Boolean] = {
    eventEnvelope.event match {
      case _: SingleResultTransaction =>
        tables.SalesDetail
          .filter(_.eventPersistenceId === eventEnvelope.persistenceId)
          .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
          .exists
          .result

      case _: MultipleResultTransaction =>
        tables.SalesDetail
          .filter(_.eventPersistenceId === eventEnvelope.persistenceId)
          .filter(_.eventSequenceNumber === BigDecimal(eventEnvelope.sequenceNr))
          .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
          .exists
          .result

      case _ =>
        DBIO.successful(false)
    }

  }

  protected[this] def updateReadModel(
      eventEnvelope: EventEnvelope,
  )(implicit traceId: TraceId, executionContext: ExecutionContext): DBIO[Option[NotificationRequest]]

  private[this] val updateDelayHistogram = Kamon
    .histogram("payment-app.rmu.update_delay", MeasurementUnit.none)
    .refine(metricTag)

  import lerna.management.stats.MetricsMultiTenantSupport._
  override def metricTag: Tags =
    Map(
      "component" -> componentName,
      "category"  -> categoryName,
    ).withTenant

  /** Cassandraへの登録からRDBへの書き込みまでのタイムラグの計測結果をKamonに送る
    * @param offset オフセット
    */
  private[this] def sendDelayTimeMetrics(offset: Offset)(implicit traceId: TraceId): Unit = {
    val timestampOption = offset match {
      case TimeBasedUUID(uuid) =>
        Try(UUIDs.unixTimestamp(uuid)).toOption
      case _ => None
    }

    timestampOption.foreach { timestamp =>
      val nowTimestamp = System.currentTimeMillis()
      val delay: Long  = nowTimestamp - timestamp
      logger.debug(s"Cassandra書き込み時間: $timestamp , RDB書き込み時間: $nowTimestamp")
      logger.debug(s"RDBへの書き込み遅延時間: $delay ms")
      updateDelayHistogram.record(delay)
    }
  }

  protected[this] def notice(
      notificationRequest: Option[NotificationRequest],
  )(implicit executionContext: ExecutionContext, traceId: TraceId): Future[NotificationResponse]

  protected[this] val noopNotice: Future[NotificationResponse] = Future.successful(NotificationSuccess())

  private[this] def insertOrUpdateOffset(currentOffset: Offset)(implicit traceId: TraceId): DBIO[Int] = {
    currentOffset match {
      case TimeBasedUUID(uuid) =>
        val sysDate = Timestamp.valueOf(dateTimeFactory.now()) // システム時間
        ReadModelUpdaterOffset insertOrUpdate ReadModelUpdaterOffsetRow(
          tagName = domainEventTag,
          offsetUuid = uuid.toString,
          insertDate = sysDate,                           // 登録日時
          insertUserId = SystemIdentify.name,             // 登録ユーザーID
          updateDate = Option(sysDate),                   // 更新日時
          updateUserId = Option(SystemIdentify.name),     // 更新ユーザーID
          versionNo = BigDecimal(0),                      // バージョン番号
          logicalDeleteFlag = LogicalDeleteFlag.unDeleted,// 論理削除フラグ
        )
      case other =>
        logger.warn("offsetが TimeBasedUUID では無い: {}", other)
        DBIO.failed(new IllegalStateException("offsetが TimeBasedUUID では無い"))
    }
  }

  def fetchOffsetUUID(): Future[Option[String]] = {
    val query = ReadModelUpdaterOffset
      .filter(_.tagName === domainEventTag)
      .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
      .map(_.offsetUuid)
    val action = query.result.headOption
    jdbcService.db.run(action)
  }

  def updateReadModelFlow()(implicit
      executor: ExecutionContext,
  ): Flow[EventEnvelope, (Option[NotificationRequest], TraceId), NotUsed] = {
    val flow = Flow[EventEnvelope].mapAsync(parallelism = 1) { event =>
      implicit val traceId: TraceId = extractTraceId(event)
      val action = for {
        alreadyUpdated <- checkAlreadyUpdated(event)
        notification <- {
          if (alreadyUpdated) {
            DBIO.successful(None)
          } else {
            updateReadModel(event)
          }
        }
        _ <- insertOrUpdateOffset(event.offset)
      } yield {
        sendDelayTimeMetrics(event.offset)
        (notification, traceId)
      }
      jdbcService.db.run(action.transactionally)
    }

    FailureSkipFlow(flow) { (eventEnvelope, throwable) =>
      implicit val traceId: TraceId = extractTraceId(eventEnvelope)

      val event         = eventEnvelope.event
      val persistenceId = eventEnvelope.persistenceId
      val sequenceNr    = eventEnvelope.sequenceNr

      logger.error(
        throwable,
        s"ReadModelの更新に失敗しました。スキップします。 persistenceId: $persistenceId, sequenceNr: $sequenceNr, event: $event",
      )
    }
  }

  def noticeResultFlow()(implicit
      executor: ExecutionContext,
  ): Flow[(Option[NotificationRequest], TraceId), NotificationResponse, NotUsed] =
    Flow[(Option[NotificationRequest], TraceId)].mapAsync(parallelism = 10) {
      case (req, _traceId) =>
        implicit val traceId: TraceId = _traceId
        notice(req)
    }

  private def extractTraceId(eventEnvelope: EventEnvelope): TraceId = {
    eventEnvelope.event match {
      case event: SalesDetailDomainEvent => event.traceId
      case _                             => TraceId.unknown
    }
  }
}
