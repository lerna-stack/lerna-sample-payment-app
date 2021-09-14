package jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail

import java.sql.{ SQLIntegrityConstraintViolationException, Timestamp }
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import akka.Done
import akka.persistence.query.{ Offset, TimeBasedUUID }
import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.SlickHandler
import com.datastax.oss.driver.api.core.uuid.Uuids
import jp.co.tis.lerna.payment.adapter.util.IllegalIncentiveRate
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.model.{
  MultipleResultTransaction,
  SalesDetailDomainEvent,
  SingleResultTransaction,
}
import jp.co.tis.lerna.payment.readmodel.constant.{ IncentiveType, LogicalDeleteFlag }
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.utility.AppRequestContext
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import kamon.metric.MeasurementUnit
import kamon.Kamon
import kamon.tag.TagSet
import lerna.log.AppLogging
import lerna.util.time.LocalDateTimeFactory
import lerna.util.trace.TraceId
import slick.jdbc.H2Profile

import scala.concurrent.{ ExecutionContext, Future }
import scala.math.BigDecimal.RoundingMode
import scala.util.Try

trait SalesDetailEventHandler[E <: SalesDetailDomainEvent]
    extends SlickHandler[EventEnvelope[E]]
    with HasSalesDetailDomainEventTag {
  this: AppLogging =>

  implicit def tenant: AppTenant

  val tables: Tables

  val dateTimeFactory: LocalDateTimeFactory

  import tables.profile.api._

  protected implicit def appRequestContext(implicit traceId: TraceId): AppRequestContext =
    AppRequestContext(traceId, tenant)

  override def start(): Future[Done] = {
    singletonCounter.increment()
    super.start()
  }

  override def stop(): Future[Done] = {
    singletonCounter.decrement()
    super.stop()
  }

  private[this] val singletonCounter = Kamon
    .gauge("payment-app.rmu.number_of_singleton")
    .withTags(metricTag)

  private[this] val updateDelayHistogram = Kamon
    .histogram("payment-app.rmu.update_delay", MeasurementUnit.none)
    .withTags(metricTag)

  import lerna.management.stats.MetricsMultiTenantSupport._
  def metricTag: TagSet = TagSet
    .from(
      Map(
        "component" -> componentName,
        "category"  -> categoryName,
      ),
    ).withTenant

  /** Cassandraへの登録からRDBへの書き込みまでのタイムラグの計測結果をKamonに送る
    * @param offset オフセット
    */
  protected def sendDelayTimeMetrics(offset: Offset)(implicit traceId: TraceId): Unit = {
    val timestampOption = offset match {
      case TimeBasedUUID(uuid) =>
        Try(Uuids.unixTimestamp(uuid)).toOption
      case _ => None
    }

    timestampOption.foreach { timestamp =>
      val nowTimestamp = System.currentTimeMillis()
      val delay: Long  = nowTimestamp - timestamp
      logger.debug(s"Cassandra書き込み時間: ${timestamp.toString} , RDB書き込み時間: ${nowTimestamp.toString}")
      logger.debug(s"RDBへの書き込み遅延時間: ${delay.toString} ms")
      updateDelayHistogram.record(delay)
    }
  }

  protected def fetchSalesDetailSeq(): DBIO[BigDecimal] = {
    // NOTE: H2DB と MariaDB で共通の文法を探すことができなかったため場合分けをしている
    // FIXME MariaDB を Oracle Mode で動かしたら1つに統一できる
    if (tables.profile.isInstanceOf[H2Profile]) {
      sql"""select SALES_DETAIL_SEQ.nextval""".as[BigDecimal].head
    } else {
      sql"""select nextval(SALES_DETAIL_SEQ)""".as[BigDecimal].head
    }
  }

  protected def fetchCashBackTempAmount(
      settlementType: String,
      amount: BigDecimal,
      systemDate: LocalDateTime,
  )(implicit
      executionContext: ExecutionContext,
      appRequestContext: AppRequestContext,
  ): DBIO[Option[BigDecimal]] = {
    import tables._
    val today = Timestamp.valueOf(systemDate.truncatedTo(ChronoUnit.DAYS))
    IncentiveMaster
      .filter(_.settlementType === settlementType)
      .filter(_.incentiveType === IncentiveType.cashback)
      .filter(_.incentiveDateFrom <= today)
      .filter(_.incentiveDateTo >= today)
      .filter(_.logicalDeleteFlag === LogicalDeleteFlag.unDeleted) // 未削除(有効)
      .map(_.incentiveRate)
      .result
      .map {
        case Nil =>
          // すべての瞬間で必ずレコードが存在することの合意が取れていないため、incentiveRate のレコードが存在しない場合はログを出さない
          BigDecimal(0)
        case Seq(Some(incentiveRate)) =>
          // キャッシュバック金額は小数切り捨て
          (incentiveRate * amount).setScale(0, RoundingMode.DOWN)
        case Seq(None) =>
          // typeがキャッシュバックなのに、incentiveRate 項目が存在しない場合
          BigDecimal(0)
        case results =>
          // 2件以上（マスター不備）
          // ※ 2件以上存在した場合に高い方を選択することは無い
          // ※ 必ず1件になるように運用するとのこと
          val msg = IllegalIncentiveRate()
          logger.error(s"""${msg.messageId}: ${msg.messageContent}""")
          BigDecimal(0)
      }
      // insertするときにOption型である必要があるため Option 化
      // ※ キャッシュバック情報が存在しない場合にNoneではなく0なのは、キャッシュバックという概念が無いレコードでnull(None)を使うため
      .map(Option.apply)
  }

  // dbへのinsertが失敗した場合はreadModelUpdaterの再起動を行い処理を再開させるためget関数を許容
  @SuppressWarnings(Array("org.wartremover.warts.TryPartial"))
  protected def ignoreDuplicate(
      dbIo: DBIO[Int],
  )(implicit executor: ExecutionContext, appRequestContext: AppRequestContext): DBIO[Int] = {
    for {
      result <- dbIo.asTry
    } yield {
      result recover {
        case _: SQLIntegrityConstraintViolationException =>
          logger.info(
            "SQLIntegrityConstraintViolationException（ReadModelUpdater起動時に余分にイベントを取得する影響で、既にTransactionLogレコードが存在するidに対してinsertが発生するが想定の範囲内のため握りつぶす）",
          )
          0
      }
    }.get
  }

  /** すでにEventが一度処理されているかチェックする
    * @param eventEnvelope EventEnvelope
    * @return 処理済みかどうか
    */
  protected def checkAlreadyUpdated(eventEnvelope: EventEnvelope[E]): DBIO[Boolean] = {
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

}
