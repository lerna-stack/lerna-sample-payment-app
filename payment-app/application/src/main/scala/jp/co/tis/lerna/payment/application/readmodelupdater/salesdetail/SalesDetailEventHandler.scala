package jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail

import java.sql.{ SQLIntegrityConstraintViolationException, Timestamp }
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import jp.co.tis.lerna.payment.adapter.notification.util.model.NotificationRequest
import jp.co.tis.lerna.payment.adapter.util.IllegalIncentiveRate
import jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.model.SalesDetailDomainEvent
import jp.co.tis.lerna.payment.readmodel.constant.{ IncentiveType, LogicalDeleteFlag }
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.utility.AppRequestContext
import lerna.log.AppLogging
import lerna.util.time.LocalDateTimeFactory
import slick.jdbc.H2Profile

import scala.concurrent.ExecutionContext
import scala.math.BigDecimal.RoundingMode

trait SalesDetailEventHandler[E <: SalesDetailDomainEvent] { this: AppLogging =>

  val tables: Tables

  val dateTimeFactory: LocalDateTimeFactory

  import tables.profile.api._

  def handle(event: E)(implicit
      executionContext: ExecutionContext,
      eventPersistenceInfo: EventPersistenceInfo,
      appRequestContext: AppRequestContext,
  ): DBIO[Option[NotificationRequest]]

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

}
