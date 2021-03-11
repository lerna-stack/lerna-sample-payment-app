package jp.co.tis.lerna.payment.readmodel.schema
// AUTO-GENERATED Slick data model
/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
@SuppressWarnings(
  Array(
    "org.wartremover.warts.AsInstanceOf",
    "org.wartremover.warts.OptionPartial",
    "org.wartremover.warts.Throw",
    "org.wartremover.contrib.warts.MissingOverride",
    "org.wartremover.contrib.warts.SomeApply",
    "lerna.warts.NamingDef",
  ),
)
trait Tables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  import slick.collection.heterogeneous._
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{ GetResult => GR }

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Array(
    Customer.schema,
    HouseMemberStore.schema,
    IncentiveMaster.schema,
    IssuingService.schema,
    SalesDetail.schema,
    ServiceRelation.schema,
  ).reduceLeft(_ ++ _)
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Customer
    *  @param customerId Database column CUSTOMER_ID SqlType(CHAR), PrimaryKey, Length(32,false)
    *  @param customerNumber Database column CUSTOMER_NUMBER SqlType(CHAR), Length(10,false)
    *  @param walletId Database column WALLET_ID SqlType(VARCHAR), Length(255,true)
    *  @param insertDate Database column INSERT_DATE SqlType(DATETIME)
    *  @param insertUserId Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param updateDate Database column UPDATE_DATE SqlType(DATETIME)
    *  @param updateUserId Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param versionNo Database column VERSION_NO SqlType(DECIMAL)
    *  @param logicalDeleteFlag Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL)
    */
  case class CustomerRow(
      customerId: String,
      customerNumber: Option[String],
      walletId: Option[String],
      insertDate: java.sql.Timestamp,
      insertUserId: String,
      updateDate: Option[java.sql.Timestamp],
      updateUserId: Option[String],
      versionNo: scala.math.BigDecimal,
      logicalDeleteFlag: scala.math.BigDecimal,
  )

  /** GetResult implicit for fetching CustomerRow objects using plain SQL queries */
  implicit def GetResultCustomerRow(implicit
      e0: GR[String],
      e1: GR[Option[String]],
      e2: GR[java.sql.Timestamp],
      e3: GR[Option[java.sql.Timestamp]],
      e4: GR[scala.math.BigDecimal],
  ): GR[CustomerRow] = GR { prs =>
    import prs._
    CustomerRow.tupled(
      (
        <<[String],
        <<?[String],
        <<?[String],
        <<[java.sql.Timestamp],
        <<[String],
        <<?[java.sql.Timestamp],
        <<?[String],
        <<[scala.math.BigDecimal],
        <<[scala.math.BigDecimal],
      ),
    )
  }

  /** Table description of table CUSTOMER. Objects of this class serve as prototypes for rows in queries. */
  class Customer(_tableTag: Tag) extends profile.api.Table[CustomerRow](_tableTag, None, "CUSTOMER") {
    def * =
      (
        customerId,
        customerNumber,
        walletId,
        insertDate,
        insertUserId,
        updateDate,
        updateUserId,
        versionNo,
        logicalDeleteFlag,
      ) <> (CustomerRow.tupled, CustomerRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(customerId),
          customerNumber,
          walletId,
          Rep.Some(insertDate),
          Rep.Some(insertUserId),
          updateDate,
          updateUserId,
          Rep.Some(versionNo),
          Rep.Some(logicalDeleteFlag),
        ),
      ).shaped.<>(
        { r =>
          import r._; _1.map(_ => CustomerRow.tupled((_1.get, _2, _3, _4.get, _5.get, _6, _7, _8.get, _9.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported."),
      )

    /** Database column CUSTOMER_ID SqlType(CHAR), PrimaryKey, Length(32,false) */
    val customerId: Rep[String] = column[String]("CUSTOMER_ID", O.PrimaryKey, O.Length(32, varying = false))

    /** Database column CUSTOMER_NUMBER SqlType(CHAR), Length(10,false) */
    val customerNumber: Rep[Option[String]] = column[Option[String]]("CUSTOMER_NUMBER", O.Length(10, varying = false))

    /** Database column WALLET_ID SqlType(VARCHAR), Length(255,true) */
    val walletId: Rep[Option[String]] = column[Option[String]]("WALLET_ID", O.Length(255, varying = true))

    /** Database column INSERT_DATE SqlType(DATETIME) */
    val insertDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("INSERT_DATE")

    /** Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true) */
    val insertUserId: Rep[String] = column[String]("INSERT_USER_ID", O.Length(32, varying = true))

    /** Database column UPDATE_DATE SqlType(DATETIME) */
    val updateDate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("UPDATE_DATE")

    /** Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true) */
    val updateUserId: Rep[Option[String]] = column[Option[String]]("UPDATE_USER_ID", O.Length(32, varying = true))

    /** Database column VERSION_NO SqlType(DECIMAL) */
    val versionNo: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("VERSION_NO")

    /** Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL) */
    val logicalDeleteFlag: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("LOGICAL_DELETE_FLAG")

    /** Uniqueness Index over (customerNumber) (database name UNIQUE_CUSTOMER) */
    val index1 = index("UNIQUE_CUSTOMER", customerNumber, unique = true)
  }

  /** Collection-like TableQuery object for table Customer */
  lazy val Customer = new TableQuery(tag => new Customer(tag))

  /** Entity class storing rows of table HouseMemberStore
    *  @param memberStoreId Database column MEMBER_STORE_ID SqlType(VARCHAR), Length(15,true)
    *  @param memberStoreNameJp Database column MEMBER_STORE_NAME_JP SqlType(VARCHAR), Length(255,true)
    *  @param memberStoreNameEn Database column MEMBER_STORE_NAME_EN SqlType(VARCHAR), Length(255,true)
    *  @param terminalId Database column TERMINAL_ID SqlType(VARCHAR), Length(255,true)
    *  @param clientId Database column CLIENT_ID SqlType(VARCHAR), Length(255,true)
    *  @param walletShopId Database column WALLET_SHOP_ID SqlType(VARCHAR), Length(40,true)
    *  @param insertDate Database column INSERT_DATE SqlType(DATETIME)
    *  @param insertUserId Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param updateDate Database column UPDATE_DATE SqlType(DATETIME)
    *  @param updateUserId Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param versionNo Database column VERSION_NO SqlType(DECIMAL)
    *  @param logicalDeleteFlag Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL)
    */
  case class HouseMemberStoreRow(
      memberStoreId: String,
      memberStoreNameJp: Option[String],
      memberStoreNameEn: Option[String],
      terminalId: String,
      clientId: String,
      walletShopId: String,
      insertDate: java.sql.Timestamp,
      insertUserId: String,
      updateDate: Option[java.sql.Timestamp],
      updateUserId: Option[String],
      versionNo: scala.math.BigDecimal,
      logicalDeleteFlag: scala.math.BigDecimal,
  )

  /** GetResult implicit for fetching HouseMemberStoreRow objects using plain SQL queries */
  implicit def GetResultHouseMemberStoreRow(implicit
      e0: GR[String],
      e1: GR[Option[String]],
      e2: GR[java.sql.Timestamp],
      e3: GR[Option[java.sql.Timestamp]],
      e4: GR[scala.math.BigDecimal],
  ): GR[HouseMemberStoreRow] = GR { prs =>
    import prs._
    HouseMemberStoreRow.tupled(
      (
        <<[String],
        <<?[String],
        <<?[String],
        <<[String],
        <<[String],
        <<[String],
        <<[java.sql.Timestamp],
        <<[String],
        <<?[java.sql.Timestamp],
        <<?[String],
        <<[scala.math.BigDecimal],
        <<[scala.math.BigDecimal],
      ),
    )
  }

  /** Table description of table HOUSE_MEMBER_STORE. Objects of this class serve as prototypes for rows in queries. */
  class HouseMemberStore(_tableTag: Tag)
      extends profile.api.Table[HouseMemberStoreRow](_tableTag, None, "HOUSE_MEMBER_STORE") {
    def * =
      (
        memberStoreId,
        memberStoreNameJp,
        memberStoreNameEn,
        terminalId,
        clientId,
        walletShopId,
        insertDate,
        insertUserId,
        updateDate,
        updateUserId,
        versionNo,
        logicalDeleteFlag,
      ) <> (HouseMemberStoreRow.tupled, HouseMemberStoreRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(memberStoreId),
          memberStoreNameJp,
          memberStoreNameEn,
          Rep.Some(terminalId),
          Rep.Some(clientId),
          Rep.Some(walletShopId),
          Rep.Some(insertDate),
          Rep.Some(insertUserId),
          updateDate,
          updateUserId,
          Rep.Some(versionNo),
          Rep.Some(logicalDeleteFlag),
        ),
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ =>
            HouseMemberStoreRow
              .tupled((_1.get, _2, _3, _4.get, _5.get, _6.get, _7.get, _8.get, _9, _10, _11.get, _12.get)),
          )
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported."),
      )

    /** Database column MEMBER_STORE_ID SqlType(VARCHAR), Length(15,true) */
    val memberStoreId: Rep[String] = column[String]("MEMBER_STORE_ID", O.Length(15, varying = true))

    /** Database column MEMBER_STORE_NAME_JP SqlType(VARCHAR), Length(255,true) */
    val memberStoreNameJp: Rep[Option[String]] =
      column[Option[String]]("MEMBER_STORE_NAME_JP", O.Length(255, varying = true))

    /** Database column MEMBER_STORE_NAME_EN SqlType(VARCHAR), Length(255,true) */
    val memberStoreNameEn: Rep[Option[String]] =
      column[Option[String]]("MEMBER_STORE_NAME_EN", O.Length(255, varying = true))

    /** Database column TERMINAL_ID SqlType(VARCHAR), Length(255,true) */
    val terminalId: Rep[String] = column[String]("TERMINAL_ID", O.Length(255, varying = true))

    /** Database column CLIENT_ID SqlType(VARCHAR), Length(255,true) */
    val clientId: Rep[String] = column[String]("CLIENT_ID", O.Length(255, varying = true))

    /** Database column WALLET_SHOP_ID SqlType(VARCHAR), Length(40,true) */
    val walletShopId: Rep[String] = column[String]("WALLET_SHOP_ID", O.Length(40, varying = true))

    /** Database column INSERT_DATE SqlType(DATETIME) */
    val insertDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("INSERT_DATE")

    /** Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true) */
    val insertUserId: Rep[String] = column[String]("INSERT_USER_ID", O.Length(32, varying = true))

    /** Database column UPDATE_DATE SqlType(DATETIME) */
    val updateDate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("UPDATE_DATE")

    /** Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true) */
    val updateUserId: Rep[Option[String]] = column[Option[String]]("UPDATE_USER_ID", O.Length(32, varying = true))

    /** Database column VERSION_NO SqlType(DECIMAL) */
    val versionNo: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("VERSION_NO")

    /** Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL) */
    val logicalDeleteFlag: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("LOGICAL_DELETE_FLAG")

    /** Primary key of HouseMemberStore (database name HOUSE_MEMBER_STORE_PK) */
    val pk = primaryKey("HOUSE_MEMBER_STORE_PK", (clientId, walletShopId))
  }

  /** Collection-like TableQuery object for table HouseMemberStore */
  lazy val HouseMemberStore = new TableQuery(tag => new HouseMemberStore(tag))

  /** Entity class storing rows of table IncentiveMaster
    *  @param incentiveMasterId Database column INCENTIVE_MASTER_ID SqlType(DECIMAL), PrimaryKey
    *  @param settlementType Database column SETTLEMENT_TYPE SqlType(CHAR), Length(2,false)
    *  @param incentiveType Database column INCENTIVE_TYPE SqlType(CHAR), Length(2,false)
    *  @param incentiveRate Database column INCENTIVE_RATE SqlType(DECIMAL)
    *  @param incentiveAmount Database column INCENTIVE_AMOUNT SqlType(DECIMAL)
    *  @param incentiveDateFrom Database column INCENTIVE_DATE_FROM SqlType(DATETIME)
    *  @param incentiveDateTo Database column INCENTIVE_DATE_TO SqlType(DATETIME)
    *  @param insertDate Database column INSERT_DATE SqlType(DATETIME)
    *  @param insertUserId Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param updateDate Database column UPDATE_DATE SqlType(DATETIME)
    *  @param updateUserId Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param versionNo Database column VERSION_NO SqlType(DECIMAL)
    *  @param logicalDeleteFlag Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL)
    */
  case class IncentiveMasterRow(
      incentiveMasterId: scala.math.BigDecimal,
      settlementType: String,
      incentiveType: String,
      incentiveRate: Option[scala.math.BigDecimal],
      incentiveAmount: Option[scala.math.BigDecimal],
      incentiveDateFrom: java.sql.Timestamp,
      incentiveDateTo: java.sql.Timestamp,
      insertDate: java.sql.Timestamp,
      insertUserId: String,
      updateDate: Option[java.sql.Timestamp],
      updateUserId: Option[String],
      versionNo: scala.math.BigDecimal,
      logicalDeleteFlag: scala.math.BigDecimal,
  )

  /** GetResult implicit for fetching IncentiveMasterRow objects using plain SQL queries */
  implicit def GetResultIncentiveMasterRow(implicit
      e0: GR[scala.math.BigDecimal],
      e1: GR[String],
      e2: GR[Option[scala.math.BigDecimal]],
      e3: GR[java.sql.Timestamp],
      e4: GR[Option[java.sql.Timestamp]],
      e5: GR[Option[String]],
  ): GR[IncentiveMasterRow] = GR { prs =>
    import prs._
    IncentiveMasterRow.tupled(
      (
        <<[scala.math.BigDecimal],
        <<[String],
        <<[String],
        <<?[scala.math.BigDecimal],
        <<?[scala.math.BigDecimal],
        <<[java.sql.Timestamp],
        <<[java.sql.Timestamp],
        <<[java.sql.Timestamp],
        <<[String],
        <<?[java.sql.Timestamp],
        <<?[String],
        <<[scala.math.BigDecimal],
        <<[scala.math.BigDecimal],
      ),
    )
  }

  /** Table description of table INCENTIVE_MASTER. Objects of this class serve as prototypes for rows in queries. */
  class IncentiveMaster(_tableTag: Tag)
      extends profile.api.Table[IncentiveMasterRow](_tableTag, None, "INCENTIVE_MASTER") {
    def * =
      (
        incentiveMasterId,
        settlementType,
        incentiveType,
        incentiveRate,
        incentiveAmount,
        incentiveDateFrom,
        incentiveDateTo,
        insertDate,
        insertUserId,
        updateDate,
        updateUserId,
        versionNo,
        logicalDeleteFlag,
      ) <> (IncentiveMasterRow.tupled, IncentiveMasterRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(incentiveMasterId),
          Rep.Some(settlementType),
          Rep.Some(incentiveType),
          incentiveRate,
          incentiveAmount,
          Rep.Some(incentiveDateFrom),
          Rep.Some(incentiveDateTo),
          Rep.Some(insertDate),
          Rep.Some(insertUserId),
          updateDate,
          updateUserId,
          Rep.Some(versionNo),
          Rep.Some(logicalDeleteFlag),
        ),
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ =>
            IncentiveMasterRow
              .tupled((_1.get, _2.get, _3.get, _4, _5, _6.get, _7.get, _8.get, _9.get, _10, _11, _12.get, _13.get)),
          )
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported."),
      )

    /** Database column INCENTIVE_MASTER_ID SqlType(DECIMAL), PrimaryKey */
    val incentiveMasterId: Rep[scala.math.BigDecimal] =
      column[scala.math.BigDecimal]("INCENTIVE_MASTER_ID", O.PrimaryKey)

    /** Database column SETTLEMENT_TYPE SqlType(CHAR), Length(2,false) */
    val settlementType: Rep[String] = column[String]("SETTLEMENT_TYPE", O.Length(2, varying = false))

    /** Database column INCENTIVE_TYPE SqlType(CHAR), Length(2,false) */
    val incentiveType: Rep[String] = column[String]("INCENTIVE_TYPE", O.Length(2, varying = false))

    /** Database column INCENTIVE_RATE SqlType(DECIMAL) */
    val incentiveRate: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("INCENTIVE_RATE")

    /** Database column INCENTIVE_AMOUNT SqlType(DECIMAL) */
    val incentiveAmount: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("INCENTIVE_AMOUNT")

    /** Database column INCENTIVE_DATE_FROM SqlType(DATETIME) */
    val incentiveDateFrom: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("INCENTIVE_DATE_FROM")

    /** Database column INCENTIVE_DATE_TO SqlType(DATETIME) */
    val incentiveDateTo: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("INCENTIVE_DATE_TO")

    /** Database column INSERT_DATE SqlType(DATETIME) */
    val insertDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("INSERT_DATE")

    /** Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true) */
    val insertUserId: Rep[String] = column[String]("INSERT_USER_ID", O.Length(32, varying = true))

    /** Database column UPDATE_DATE SqlType(DATETIME) */
    val updateDate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("UPDATE_DATE")

    /** Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true) */
    val updateUserId: Rep[Option[String]] = column[Option[String]]("UPDATE_USER_ID", O.Length(32, varying = true))

    /** Database column VERSION_NO SqlType(DECIMAL) */
    val versionNo: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("VERSION_NO")

    /** Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL) */
    val logicalDeleteFlag: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("LOGICAL_DELETE_FLAG")
  }

  /** Collection-like TableQuery object for table IncentiveMaster */
  lazy val IncentiveMaster = new TableQuery(tag => new IncentiveMaster(tag))

  /** Entity class storing rows of table IssuingService
    *  @param contractNumber Database column CONTRACT_NUMBER SqlType(CHAR), PrimaryKey, Length(10,false)
    *  @param housePan Database column HOUSE_PAN SqlType(VARCHAR), Length(16,true)
    *  @param serviceRelationId Database column SERVICE_RELATION_ID SqlType(DECIMAL)
    *  @param digit4byte Database column DIGIT_4BYTE SqlType(CHAR), Length(4,false)
    *  @param insertDate Database column INSERT_DATE SqlType(DATETIME)
    *  @param insertUserId Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param updateDate Database column UPDATE_DATE SqlType(DATETIME)
    *  @param updateUserId Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param versionNo Database column VERSION_NO SqlType(DECIMAL)
    *  @param logicalDeleteFlag Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL)
    */
  case class IssuingServiceRow(
      contractNumber: String,
      housePan: Option[String],
      serviceRelationId: Option[scala.math.BigDecimal],
      digit4byte: Option[String],
      insertDate: java.sql.Timestamp,
      insertUserId: String,
      updateDate: Option[java.sql.Timestamp],
      updateUserId: Option[String],
      versionNo: scala.math.BigDecimal,
      logicalDeleteFlag: scala.math.BigDecimal,
  )

  /** GetResult implicit for fetching IssuingServiceRow objects using plain SQL queries */
  implicit def GetResultIssuingServiceRow(implicit
      e0: GR[String],
      e1: GR[Option[String]],
      e2: GR[Option[scala.math.BigDecimal]],
      e3: GR[java.sql.Timestamp],
      e4: GR[Option[java.sql.Timestamp]],
      e5: GR[scala.math.BigDecimal],
  ): GR[IssuingServiceRow] = GR { prs =>
    import prs._
    IssuingServiceRow.tupled(
      (
        <<[String],
        <<?[String],
        <<?[scala.math.BigDecimal],
        <<?[String],
        <<[java.sql.Timestamp],
        <<[String],
        <<?[java.sql.Timestamp],
        <<?[String],
        <<[scala.math.BigDecimal],
        <<[scala.math.BigDecimal],
      ),
    )
  }

  /** Table description of table ISSUING_SERVICE. Objects of this class serve as prototypes for rows in queries. */
  class IssuingService(_tableTag: Tag)
      extends profile.api.Table[IssuingServiceRow](_tableTag, None, "ISSUING_SERVICE") {
    def * =
      (
        contractNumber,
        housePan,
        serviceRelationId,
        digit4byte,
        insertDate,
        insertUserId,
        updateDate,
        updateUserId,
        versionNo,
        logicalDeleteFlag,
      ) <> (IssuingServiceRow.tupled, IssuingServiceRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(contractNumber),
          housePan,
          serviceRelationId,
          digit4byte,
          Rep.Some(insertDate),
          Rep.Some(insertUserId),
          updateDate,
          updateUserId,
          Rep.Some(versionNo),
          Rep.Some(logicalDeleteFlag),
        ),
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ => IssuingServiceRow.tupled((_1.get, _2, _3, _4, _5.get, _6.get, _7, _8, _9.get, _10.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported."),
      )

    /** Database column CONTRACT_NUMBER SqlType(CHAR), PrimaryKey, Length(10,false) */
    val contractNumber: Rep[String] = column[String]("CONTRACT_NUMBER", O.PrimaryKey, O.Length(10, varying = false))

    /** Database column HOUSE_PAN SqlType(VARCHAR), Length(16,true) */
    val housePan: Rep[Option[String]] = column[Option[String]]("HOUSE_PAN", O.Length(16, varying = true))

    /** Database column SERVICE_RELATION_ID SqlType(DECIMAL) */
    val serviceRelationId: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("SERVICE_RELATION_ID")

    /** Database column DIGIT_4BYTE SqlType(CHAR), Length(4,false) */
    val digit4byte: Rep[Option[String]] = column[Option[String]]("DIGIT_4BYTE", O.Length(4, varying = false))

    /** Database column INSERT_DATE SqlType(DATETIME) */
    val insertDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("INSERT_DATE")

    /** Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true) */
    val insertUserId: Rep[String] = column[String]("INSERT_USER_ID", O.Length(32, varying = true))

    /** Database column UPDATE_DATE SqlType(DATETIME) */
    val updateDate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("UPDATE_DATE")

    /** Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true) */
    val updateUserId: Rep[Option[String]] = column[Option[String]]("UPDATE_USER_ID", O.Length(32, varying = true))

    /** Database column VERSION_NO SqlType(DECIMAL) */
    val versionNo: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("VERSION_NO")

    /** Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL) */
    val logicalDeleteFlag: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("LOGICAL_DELETE_FLAG")

    /** Foreign key referencing ServiceRelation (database name ISSUING_SERVICE_ibfk_1) */
    lazy val serviceRelationFk = foreignKey("ISSUING_SERVICE_ibfk_1", serviceRelationId, ServiceRelation)(
      r => Rep.Some(r.serviceRelationId),
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Restrict,
    )
  }

  /** Collection-like TableQuery object for table IssuingService */
  lazy val IssuingService = new TableQuery(tag => new IssuingService(tag))

  /** Entity class storing rows of table SalesDetail
    *  @param walletSettlementId Database column WALLET_SETTLEMENT_ID SqlType(DECIMAL), PrimaryKey
    *  @param settlementType Database column SETTLEMENT_TYPE SqlType(CHAR), Length(2,false)
    *  @param walletId Database column WALLET_ID SqlType(VARCHAR), Length(255,true)
    *  @param customerNumber Database column CUSTOMER_NUMBER SqlType(CHAR), Length(10,false)
    *  @param dealStatus Database column DEAL_STATUS SqlType(CHAR), Length(2,false)
    *  @param specificDealInfo Database column SPECIFIC_DEAL_INFO SqlType(VARCHAR), Length(20,true)
    *  @param originDealId Database column ORIGIN_DEAL_ID SqlType(VARCHAR), Length(20,true)
    *  @param contractNumber Database column CONTRACT_NUMBER SqlType(CHAR), Length(10,false)
    *  @param maskingInfo Database column MASKING_INFO SqlType(VARCHAR), Length(16,true)
    *  @param saleDatetime Database column SALE_DATETIME SqlType(DATETIME)
    *  @param dealDate Database column DEAL_DATE SqlType(DATETIME)
    *  @param saleCancelType Database column SALE_CANCEL_TYPE SqlType(CHAR), Length(2,false)
    *  @param sendDatetime Database column SEND_DATETIME SqlType(DATETIME)
    *  @param authoriNumber Database column AUTHORI_NUMBER SqlType(DECIMAL)
    *  @param amount Database column AMOUNT SqlType(DECIMAL)
    *  @param memberStoreId Database column MEMBER_STORE_ID SqlType(VARCHAR), Length(255,true)
    *  @param memberStorePosId Database column MEMBER_STORE_POS_ID SqlType(VARCHAR), Length(255,true)
    *  @param memberStoreName Database column MEMBER_STORE_NAME SqlType(VARCHAR), Length(255,true)
    *  @param failureCancelFlag Database column FAILURE_CANCEL_FLAG SqlType(DECIMAL)
    *  @param errorCode Database column ERROR_CODE SqlType(VARCHAR), Length(255,true)
    *  @param dealSerialNumber Database column DEAL_SERIAL_NUMBER SqlType(CHAR), Length(12,false)
    *  @param paymentId Database column PAYMENT_ID SqlType(CHAR), Length(5,false)
    *  @param originalPaymentId Database column ORIGINAL_PAYMENT_ID SqlType(CHAR), Length(5,false)
    *  @param cashBackTempAmount Database column CASH_BACK_TEMP_AMOUNT SqlType(DECIMAL)
    *  @param cashBackFixedAmount Database column CASH_BACK_FIXED_AMOUNT SqlType(DECIMAL)
    *  @param applicationExtractFlag Database column APPLICATION_EXTRACT_FLAG SqlType(DECIMAL)
    *  @param customerId Database column CUSTOMER_ID SqlType(CHAR), Length(32,false)
    *  @param saleExtractedFlag Database column SALE_EXTRACTED_FLAG SqlType(DECIMAL)
    *  @param eventPersistenceId Database column EVENT_PERSISTENCE_ID SqlType(VARCHAR), Length(255,true)
    *  @param eventSequenceNumber Database column EVENT_SEQUENCE_NUMBER SqlType(DECIMAL)
    *  @param insertDate Database column INSERT_DATE SqlType(DATETIME)
    *  @param insertUserId Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param updateDate Database column UPDATE_DATE SqlType(DATETIME)
    *  @param updateUserId Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param versionNo Database column VERSION_NO SqlType(DECIMAL)
    *  @param logicalDeleteFlag Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL)
    */
  case class SalesDetailRow(
      walletSettlementId: scala.math.BigDecimal,
      settlementType: Option[String],
      walletId: Option[String],
      customerNumber: Option[String],
      dealStatus: Option[String],
      specificDealInfo: Option[String],
      originDealId: Option[String],
      contractNumber: Option[String],
      maskingInfo: Option[String],
      saleDatetime: Option[java.sql.Timestamp],
      dealDate: Option[java.sql.Timestamp],
      saleCancelType: Option[String],
      sendDatetime: Option[java.sql.Timestamp],
      authoriNumber: Option[scala.math.BigDecimal],
      amount: Option[scala.math.BigDecimal],
      memberStoreId: Option[String],
      memberStorePosId: Option[String],
      memberStoreName: Option[String],
      failureCancelFlag: Option[scala.math.BigDecimal],
      errorCode: Option[String],
      dealSerialNumber: Option[String],
      paymentId: Option[String],
      originalPaymentId: Option[String],
      cashBackTempAmount: Option[scala.math.BigDecimal],
      cashBackFixedAmount: Option[scala.math.BigDecimal],
      applicationExtractFlag: Option[scala.math.BigDecimal],
      customerId: Option[String],
      saleExtractedFlag: Option[scala.math.BigDecimal],
      eventPersistenceId: Option[String],
      eventSequenceNumber: Option[scala.math.BigDecimal],
      insertDate: java.sql.Timestamp,
      insertUserId: String,
      updateDate: Option[java.sql.Timestamp],
      updateUserId: Option[String],
      versionNo: scala.math.BigDecimal,
      logicalDeleteFlag: scala.math.BigDecimal,
  )

  /** GetResult implicit for fetching SalesDetailRow objects using plain SQL queries */
  implicit def GetResultSalesDetailRow(implicit
      e0: GR[scala.math.BigDecimal],
      e1: GR[Option[String]],
      e2: GR[Option[java.sql.Timestamp]],
      e3: GR[Option[scala.math.BigDecimal]],
      e4: GR[java.sql.Timestamp],
      e5: GR[String],
  ): GR[SalesDetailRow] = GR { prs =>
    import prs._
    SalesDetailRow(
      <<[scala.math.BigDecimal],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[java.sql.Timestamp],
      <<?[java.sql.Timestamp],
      <<?[String],
      <<?[java.sql.Timestamp],
      <<?[scala.math.BigDecimal],
      <<?[scala.math.BigDecimal],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[scala.math.BigDecimal],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[String],
      <<?[scala.math.BigDecimal],
      <<?[scala.math.BigDecimal],
      <<?[scala.math.BigDecimal],
      <<?[String],
      <<?[scala.math.BigDecimal],
      <<?[String],
      <<?[scala.math.BigDecimal],
      <<[java.sql.Timestamp],
      <<[String],
      <<?[java.sql.Timestamp],
      <<?[String],
      <<[scala.math.BigDecimal],
      <<[scala.math.BigDecimal],
    )
  }

  /** Table description of table SALES_DETAIL. Objects of this class serve as prototypes for rows in queries. */
  class SalesDetail(_tableTag: Tag) extends profile.api.Table[SalesDetailRow](_tableTag, None, "SALES_DETAIL") {
    def * =
      (walletSettlementId :: settlementType :: walletId :: customerNumber :: dealStatus :: specificDealInfo :: originDealId :: contractNumber :: maskingInfo :: saleDatetime :: dealDate :: saleCancelType :: sendDatetime :: authoriNumber :: amount :: memberStoreId :: memberStorePosId :: memberStoreName :: failureCancelFlag :: errorCode :: dealSerialNumber :: paymentId :: originalPaymentId :: cashBackTempAmount :: cashBackFixedAmount :: applicationExtractFlag :: customerId :: saleExtractedFlag :: eventPersistenceId :: eventSequenceNumber :: insertDate :: insertUserId :: updateDate :: updateUserId :: versionNo :: logicalDeleteFlag :: HNil)
        .mapTo[SalesDetailRow]

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (Rep.Some(
        walletSettlementId,
      ) :: settlementType :: walletId :: customerNumber :: dealStatus :: specificDealInfo :: originDealId :: contractNumber :: maskingInfo :: saleDatetime :: dealDate :: saleCancelType :: sendDatetime :: authoriNumber :: amount :: memberStoreId :: memberStorePosId :: memberStoreName :: failureCancelFlag :: errorCode :: dealSerialNumber :: paymentId :: originalPaymentId :: cashBackTempAmount :: cashBackFixedAmount :: applicationExtractFlag :: customerId :: saleExtractedFlag :: eventPersistenceId :: eventSequenceNumber :: Rep
        .Some(insertDate) :: Rep.Some(insertUserId) :: updateDate :: updateUserId :: Rep.Some(versionNo) :: Rep.Some(
        logicalDeleteFlag,
      ) :: HNil).shaped.<>(
        r =>
          SalesDetailRow(
            r(0).asInstanceOf[Option[scala.math.BigDecimal]].get,
            r(1).asInstanceOf[Option[String]],
            r(2).asInstanceOf[Option[String]],
            r(3).asInstanceOf[Option[String]],
            r(4).asInstanceOf[Option[String]],
            r(5).asInstanceOf[Option[String]],
            r(6).asInstanceOf[Option[String]],
            r(7).asInstanceOf[Option[String]],
            r(8).asInstanceOf[Option[String]],
            r(9).asInstanceOf[Option[java.sql.Timestamp]],
            r(10).asInstanceOf[Option[java.sql.Timestamp]],
            r(11).asInstanceOf[Option[String]],
            r(12).asInstanceOf[Option[java.sql.Timestamp]],
            r(13).asInstanceOf[Option[scala.math.BigDecimal]],
            r(14).asInstanceOf[Option[scala.math.BigDecimal]],
            r(15).asInstanceOf[Option[String]],
            r(16).asInstanceOf[Option[String]],
            r(17).asInstanceOf[Option[String]],
            r(18).asInstanceOf[Option[scala.math.BigDecimal]],
            r(19).asInstanceOf[Option[String]],
            r(20).asInstanceOf[Option[String]],
            r(21).asInstanceOf[Option[String]],
            r(22).asInstanceOf[Option[String]],
            r(23).asInstanceOf[Option[scala.math.BigDecimal]],
            r(24).asInstanceOf[Option[scala.math.BigDecimal]],
            r(25).asInstanceOf[Option[scala.math.BigDecimal]],
            r(26).asInstanceOf[Option[String]],
            r(27).asInstanceOf[Option[scala.math.BigDecimal]],
            r(28).asInstanceOf[Option[String]],
            r(29).asInstanceOf[Option[scala.math.BigDecimal]],
            r(30).asInstanceOf[Option[java.sql.Timestamp]].get,
            r(31).asInstanceOf[Option[String]].get,
            r(32).asInstanceOf[Option[java.sql.Timestamp]],
            r(33).asInstanceOf[Option[String]],
            r(34).asInstanceOf[Option[scala.math.BigDecimal]].get,
            r(35).asInstanceOf[Option[scala.math.BigDecimal]].get,
          ),
        (_: Any) => throw new Exception("Inserting into ? projection not supported."),
      )

    /** Database column WALLET_SETTLEMENT_ID SqlType(DECIMAL), PrimaryKey */
    val walletSettlementId: Rep[scala.math.BigDecimal] =
      column[scala.math.BigDecimal]("WALLET_SETTLEMENT_ID", O.PrimaryKey)

    /** Database column SETTLEMENT_TYPE SqlType(CHAR), Length(2,false) */
    val settlementType: Rep[Option[String]] = column[Option[String]]("SETTLEMENT_TYPE", O.Length(2, varying = false))

    /** Database column WALLET_ID SqlType(VARCHAR), Length(255,true) */
    val walletId: Rep[Option[String]] = column[Option[String]]("WALLET_ID", O.Length(255, varying = true))

    /** Database column CUSTOMER_NUMBER SqlType(CHAR), Length(10,false) */
    val customerNumber: Rep[Option[String]] = column[Option[String]]("CUSTOMER_NUMBER", O.Length(10, varying = false))

    /** Database column DEAL_STATUS SqlType(CHAR), Length(2,false) */
    val dealStatus: Rep[Option[String]] = column[Option[String]]("DEAL_STATUS", O.Length(2, varying = false))

    /** Database column SPECIFIC_DEAL_INFO SqlType(VARCHAR), Length(20,true) */
    val specificDealInfo: Rep[Option[String]] =
      column[Option[String]]("SPECIFIC_DEAL_INFO", O.Length(20, varying = true))

    /** Database column ORIGIN_DEAL_ID SqlType(VARCHAR), Length(20,true) */
    val originDealId: Rep[Option[String]] = column[Option[String]]("ORIGIN_DEAL_ID", O.Length(20, varying = true))

    /** Database column CONTRACT_NUMBER SqlType(CHAR), Length(10,false) */
    val contractNumber: Rep[Option[String]] = column[Option[String]]("CONTRACT_NUMBER", O.Length(10, varying = false))

    /** Database column MASKING_INFO SqlType(VARCHAR), Length(16,true) */
    val maskingInfo: Rep[Option[String]] = column[Option[String]]("MASKING_INFO", O.Length(16, varying = true))

    /** Database column SALE_DATETIME SqlType(DATETIME) */
    val saleDatetime: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("SALE_DATETIME")

    /** Database column DEAL_DATE SqlType(DATETIME) */
    val dealDate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("DEAL_DATE")

    /** Database column SALE_CANCEL_TYPE SqlType(CHAR), Length(2,false) */
    val saleCancelType: Rep[Option[String]] = column[Option[String]]("SALE_CANCEL_TYPE", O.Length(2, varying = false))

    /** Database column SEND_DATETIME SqlType(DATETIME) */
    val sendDatetime: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("SEND_DATETIME")

    /** Database column AUTHORI_NUMBER SqlType(DECIMAL) */
    val authoriNumber: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("AUTHORI_NUMBER")

    /** Database column AMOUNT SqlType(DECIMAL) */
    val amount: Rep[Option[scala.math.BigDecimal]] = column[Option[scala.math.BigDecimal]]("AMOUNT")

    /** Database column MEMBER_STORE_ID SqlType(VARCHAR), Length(255,true) */
    val memberStoreId: Rep[Option[String]] = column[Option[String]]("MEMBER_STORE_ID", O.Length(255, varying = true))

    /** Database column MEMBER_STORE_POS_ID SqlType(VARCHAR), Length(255,true) */
    val memberStorePosId: Rep[Option[String]] =
      column[Option[String]]("MEMBER_STORE_POS_ID", O.Length(255, varying = true))

    /** Database column MEMBER_STORE_NAME SqlType(VARCHAR), Length(255,true) */
    val memberStoreName: Rep[Option[String]] =
      column[Option[String]]("MEMBER_STORE_NAME", O.Length(255, varying = true))

    /** Database column FAILURE_CANCEL_FLAG SqlType(DECIMAL) */
    val failureCancelFlag: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("FAILURE_CANCEL_FLAG")

    /** Database column ERROR_CODE SqlType(VARCHAR), Length(255,true) */
    val errorCode: Rep[Option[String]] = column[Option[String]]("ERROR_CODE", O.Length(255, varying = true))

    /** Database column DEAL_SERIAL_NUMBER SqlType(CHAR), Length(12,false) */
    val dealSerialNumber: Rep[Option[String]] =
      column[Option[String]]("DEAL_SERIAL_NUMBER", O.Length(12, varying = false))

    /** Database column PAYMENT_ID SqlType(CHAR), Length(5,false) */
    val paymentId: Rep[Option[String]] = column[Option[String]]("PAYMENT_ID", O.Length(5, varying = false))

    /** Database column ORIGINAL_PAYMENT_ID SqlType(CHAR), Length(5,false) */
    val originalPaymentId: Rep[Option[String]] =
      column[Option[String]]("ORIGINAL_PAYMENT_ID", O.Length(5, varying = false))

    /** Database column CASH_BACK_TEMP_AMOUNT SqlType(DECIMAL) */
    val cashBackTempAmount: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("CASH_BACK_TEMP_AMOUNT")

    /** Database column CASH_BACK_FIXED_AMOUNT SqlType(DECIMAL) */
    val cashBackFixedAmount: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("CASH_BACK_FIXED_AMOUNT")

    /** Database column APPLICATION_EXTRACT_FLAG SqlType(DECIMAL) */
    val applicationExtractFlag: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("APPLICATION_EXTRACT_FLAG")

    /** Database column CUSTOMER_ID SqlType(CHAR), Length(32,false) */
    val customerId: Rep[Option[String]] = column[Option[String]]("CUSTOMER_ID", O.Length(32, varying = false))

    /** Database column SALE_EXTRACTED_FLAG SqlType(DECIMAL) */
    val saleExtractedFlag: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("SALE_EXTRACTED_FLAG")

    /** Database column EVENT_PERSISTENCE_ID SqlType(VARCHAR), Length(255,true) */
    val eventPersistenceId: Rep[Option[String]] =
      column[Option[String]]("EVENT_PERSISTENCE_ID", O.Length(255, varying = true))

    /** Database column EVENT_SEQUENCE_NUMBER SqlType(DECIMAL) */
    val eventSequenceNumber: Rep[Option[scala.math.BigDecimal]] =
      column[Option[scala.math.BigDecimal]]("EVENT_SEQUENCE_NUMBER")

    /** Database column INSERT_DATE SqlType(DATETIME) */
    val insertDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("INSERT_DATE")

    /** Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true) */
    val insertUserId: Rep[String] = column[String]("INSERT_USER_ID", O.Length(32, varying = true))

    /** Database column UPDATE_DATE SqlType(DATETIME) */
    val updateDate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("UPDATE_DATE")

    /** Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true) */
    val updateUserId: Rep[Option[String]] = column[Option[String]]("UPDATE_USER_ID", O.Length(32, varying = true))

    /** Database column VERSION_NO SqlType(DECIMAL) */
    val versionNo: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("VERSION_NO")

    /** Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL) */
    val logicalDeleteFlag: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("LOGICAL_DELETE_FLAG")

    /** Foreign key referencing Customer (database name SYS_C0038684) */
    lazy val customerFk = foreignKey("SYS_C0038684", customerId :: HNil, Customer)(
      r => Rep.Some(r.customerId) :: HNil,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Restrict,
    )

    /** Index over (eventPersistenceId) (database name IX_SALES_DETAIL) */
    val index1 = index("IX_SALES_DETAIL", eventPersistenceId :: HNil)
  }

  /** Collection-like TableQuery object for table SalesDetail */
  lazy val SalesDetail = new TableQuery(tag => new SalesDetail(tag))

  /** Entity class storing rows of table ServiceRelation
    *  @param serviceRelationId Database column SERVICE_RELATION_ID SqlType(DECIMAL), PrimaryKey
    *  @param foreignKeyType Database column FOREIGN_KEY_TYPE SqlType(CHAR), Length(2,false)
    *  @param customerId Database column CUSTOMER_ID SqlType(CHAR), Length(32,false)
    *  @param insertDate Database column INSERT_DATE SqlType(DATETIME)
    *  @param insertUserId Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param updateDate Database column UPDATE_DATE SqlType(DATETIME)
    *  @param updateUserId Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true)
    *  @param versionNo Database column VERSION_NO SqlType(DECIMAL)
    *  @param logicalDeleteFlag Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL)
    */
  case class ServiceRelationRow(
      serviceRelationId: scala.math.BigDecimal,
      foreignKeyType: String,
      customerId: String,
      insertDate: java.sql.Timestamp,
      insertUserId: String,
      updateDate: Option[java.sql.Timestamp],
      updateUserId: Option[String],
      versionNo: scala.math.BigDecimal,
      logicalDeleteFlag: scala.math.BigDecimal,
  )

  /** GetResult implicit for fetching ServiceRelationRow objects using plain SQL queries */
  implicit def GetResultServiceRelationRow(implicit
      e0: GR[scala.math.BigDecimal],
      e1: GR[String],
      e2: GR[java.sql.Timestamp],
      e3: GR[Option[java.sql.Timestamp]],
      e4: GR[Option[String]],
  ): GR[ServiceRelationRow] = GR { prs =>
    import prs._
    ServiceRelationRow.tupled(
      (
        <<[scala.math.BigDecimal],
        <<[String],
        <<[String],
        <<[java.sql.Timestamp],
        <<[String],
        <<?[java.sql.Timestamp],
        <<?[String],
        <<[scala.math.BigDecimal],
        <<[scala.math.BigDecimal],
      ),
    )
  }

  /** Table description of table SERVICE_RELATION. Objects of this class serve as prototypes for rows in queries. */
  class ServiceRelation(_tableTag: Tag)
      extends profile.api.Table[ServiceRelationRow](_tableTag, None, "SERVICE_RELATION") {
    def * =
      (
        serviceRelationId,
        foreignKeyType,
        customerId,
        insertDate,
        insertUserId,
        updateDate,
        updateUserId,
        versionNo,
        logicalDeleteFlag,
      ) <> (ServiceRelationRow.tupled, ServiceRelationRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? =
      (
        (
          Rep.Some(serviceRelationId),
          Rep.Some(foreignKeyType),
          Rep.Some(customerId),
          Rep.Some(insertDate),
          Rep.Some(insertUserId),
          updateDate,
          updateUserId,
          Rep.Some(versionNo),
          Rep.Some(logicalDeleteFlag),
        ),
      ).shaped.<>(
        { r =>
          import r._;
          _1.map(_ => ServiceRelationRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7, _8.get, _9.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported."),
      )

    /** Database column SERVICE_RELATION_ID SqlType(DECIMAL), PrimaryKey */
    val serviceRelationId: Rep[scala.math.BigDecimal] =
      column[scala.math.BigDecimal]("SERVICE_RELATION_ID", O.PrimaryKey)

    /** Database column FOREIGN_KEY_TYPE SqlType(CHAR), Length(2,false) */
    val foreignKeyType: Rep[String] = column[String]("FOREIGN_KEY_TYPE", O.Length(2, varying = false))

    /** Database column CUSTOMER_ID SqlType(CHAR), Length(32,false) */
    val customerId: Rep[String] = column[String]("CUSTOMER_ID", O.Length(32, varying = false))

    /** Database column INSERT_DATE SqlType(DATETIME) */
    val insertDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("INSERT_DATE")

    /** Database column INSERT_USER_ID SqlType(VARCHAR), Length(32,true) */
    val insertUserId: Rep[String] = column[String]("INSERT_USER_ID", O.Length(32, varying = true))

    /** Database column UPDATE_DATE SqlType(DATETIME) */
    val updateDate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("UPDATE_DATE")

    /** Database column UPDATE_USER_ID SqlType(VARCHAR), Length(32,true) */
    val updateUserId: Rep[Option[String]] = column[Option[String]]("UPDATE_USER_ID", O.Length(32, varying = true))

    /** Database column VERSION_NO SqlType(DECIMAL) */
    val versionNo: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("VERSION_NO")

    /** Database column LOGICAL_DELETE_FLAG SqlType(DECIMAL) */
    val logicalDeleteFlag: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("LOGICAL_DELETE_FLAG")

    /** Foreign key referencing Customer (database name SYS_C0038685) */
    lazy val customerFk = foreignKey("SYS_C0038685", customerId, Customer)(
      r => r.customerId,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Restrict,
    )
  }

  /** Collection-like TableQuery object for table ServiceRelation */
  lazy val ServiceRelation = new TableQuery(tag => new ServiceRelation(tag))
}
