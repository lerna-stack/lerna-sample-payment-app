package jp.co.tis.lerna.payment.readmodel

import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.readmodel.constant.LogicalDeleteFlag
import jp.co.tis.lerna.payment.readmodel.schema.Tables

object TableSeeds {

  import scala.util.Random

  def apply(tables: Tables): TableSeeds = new TableSeeds(tables)

  trait ValueGenerator[+T] extends (() => T)

  private def arbitraryValue[T](implicit gen: ValueGenerator[T]): T = gen()

  private implicit def optionValueGenerator[T](implicit gen: ValueGenerator[T]): ValueGenerator[Option[T]] =
    () => Option(gen())

  private implicit object StringValueGenerator extends ValueGenerator[String] {
    override def apply(): String = Random.alphanumeric.take(2).mkString
  }
  private implicit object BigDecimalValueGenerator extends ValueGenerator[scala.math.BigDecimal] {
    override def apply(): scala.math.BigDecimal = BigDecimal(Random.nextInt(10))
  }
  private implicit object TimestampValueGenerator extends ValueGenerator[java.sql.Timestamp] {
    override def apply(): java.sql.Timestamp = new java.sql.Timestamp(Random.nextInt(Int.MaxValue))
  }

  private val logicalDeleteFlagAsNotDeleted: BigDecimal = LogicalDeleteFlag.unDeleted
}

class TableSeeds private (val tables: Tables) {
  import TableSeeds._

  lazy val CustomerRowSeed = tables.CustomerRow(
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    logicalDeleteFlagAsNotDeleted,
  )

  lazy val HouseMemberStoreRowSeed = tables.HouseMemberStoreRow(
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    logicalDeleteFlagAsNotDeleted,
  )

  lazy val IncentiveMasterRowSeed = tables.IncentiveMasterRow(
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    logicalDeleteFlagAsNotDeleted,
  )
  lazy val IssuingServiceRowSeed = tables.IssuingServiceRow(
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    logicalDeleteFlagAsNotDeleted,
  )
  lazy val SalesDetailRowSeed = tables.SalesDetailRow(
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    logicalDeleteFlagAsNotDeleted,
  )
  lazy val ServiceRelationRowSeed = tables.ServiceRelationRow(
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    arbitraryValue,
    logicalDeleteFlagAsNotDeleted,
  )
}
