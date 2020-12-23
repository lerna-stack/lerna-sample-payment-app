# IMPORTANT NOTE

## 問題点

次のようなテーブル定義を発行した MariaDB のデータベースに対して、
slick-codegen を実行すると、`java.lang.NumberFormatException`　が発生しました。

原因を調査すると、 `NOT_NULL` が指定されていないカラムの定義が原因でエラーが出ているようでした。
`EVENT_SEQUENCE_NUMBER DECIMAL(20),`

```SQL
CREATE TABLE SALES_DETAIL (
  WALLET_SETTLEMENT_ID DECIMAL NOT NULL,
  SETTLEMENT_TYPE CHAR(2),
  WALLET_ID VARCHAR(255),
  CUSTOMER_NUMBER CHAR(10),
  DEAL_STATUS CHAR(2),
  SPECIFIC_DEAL_INFO VARCHAR(20),
  ORIGIN_DEAL_ID VARCHAR(20),
  CONTRACT_NUMBER CHAR(10),
  MASKING_INFO VARCHAR(16),
  SALE_DATETIME DATETIME,
  DEAL_DATE DATETIME,
  SALE_CANCEL_TYPE CHAR(2),
  SEND_DATETIME DATETIME,
  AUTHORI_NUMBER DECIMAL(7) DEFAULT NULL,
  AMOUNT DECIMAL(12) DEFAULT NULL,
  MEMBER_STORE_ID VARCHAR(255),
  MEMBER_STORE_POS_ID VARCHAR(255),
  MEMBER_STORE_NAME VARCHAR(255),
  FAILURE_CANCEL_FLAG DECIMAL(1) DEFAULT NULL,
  ERROR_CODE VARCHAR(255),
  DEAL_SERIAL_NUMBER CHAR(12),
  PAYMENT_ID CHAR(5),
  ORIGINAL_PAYMENT_ID CHAR(5),
  CASH_BACK_TEMP_AMOUNT DECIMAL DEFAULT NULL,
  CASH_BACK_FIXED_AMOUNT DECIMAL DEFAULT NULL,
  APPLICATION_EXTRACT_FLAG DECIMAL(1) DEFAULT NULL,
  CUSTOMER_ID CHAR(32),
  SALE_EXTRACTED_FLAG DECIMAL(1) DEFAULT NULL,
  EVENT_PERSISTENCE_ID VARCHAR(255),
  EVENT_SEQUENCE_NUMBER DECIMAL(20) DEFAULT NULL,
  INSERT_DATE DATETIME NOT NULL,
  INSERT_USER_ID VARCHAR(32) NOT NULL,
  UPDATE_DATE DATETIME,
  UPDATE_USER_ID VARCHAR(32),
  VERSION_NO DECIMAL(9) NOT NULL,
  LOGICAL_DELETE_FLAG DECIMAL(1) NOT NULL
);
```

```shell
$ sbt slick-codegen/run
[info] welcome to sbt 1.3.13 (Oracle Corporation Java 1.8.0_211)
[info] loading settings for project lerna-sample-payment-app-build from build.sbt,plugins.sbt ...
[info] loading project definition from C:\Users\***\workspace\lerna-sample-payment-app\project
[info] loading settings for project lerna-sample-payment-app from build.sbt ...
[info] resolving key references (18897 settings) ...
[info] set current project to lerna-sample-payment-app (in build file:/C:/Users/***/workspace/lerna-sample-payment-app/)
[info] running (fork) Codegen
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.
Exception in thread "main" java.lang.NumberFormatException
        at java.math.BigDecimal.<init>(BigDecimal.java:497)
        at java.math.BigDecimal.<init>(BigDecimal.java:383)
        at java.math.BigDecimal.<init>(BigDecimal.java:809)
        at scala.math.BigDecimal$.exact(BigDecimal.scala:126)
        at scala.math.BigDecimal$.apply(BigDecimal.scala:284)
        at slick.jdbc.MySQLProfile$ModelBuilder$ColumnBuilder$$anonfun$default$2.applyOrElse(MySQLProfile.scala:94)
        at slick.jdbc.MySQLProfile$ModelBuilder$ColumnBuilder$$anonfun$default$2.applyOrElse(MySQLProfile.scala:90)
        at scala.PartialFunction$Lifted.apply(PartialFunction.scala:228)
        at scala.PartialFunction$Lifted.apply(PartialFunction.scala:224)
        at scala.Option.collect(Option.scala:432)
        at slick.jdbc.MySQLProfile$ModelBuilder$ColumnBuilder.default(MySQLProfile.scala:90)
        at slick.jdbc.JdbcModelBuilder$ColumnBuilder.$anonfun$defaultColumnOption$3(JdbcModelBuilder.scala:255)
        at scala.Option.getOrElse(Option.scala:189)
        at slick.jdbc.JdbcModelBuilder$ColumnBuilder.defaultColumnOption(JdbcModelBuilder.scala:255)
        at slick.jdbc.JdbcModelBuilder$ColumnBuilder.convenientDefault(JdbcModelBuilder.scala:264)
        at slick.jdbc.JdbcModelBuilder$ColumnBuilder.model(JdbcModelBuilder.scala:282)
        at slick.jdbc.JdbcModelBuilder$TableBuilder.$anonfun$columns$1(JdbcModelBuilder.scala:162)
        at scala.collection.TraversableLike.$anonfun$map$1(TraversableLike.scala:285)
        at scala.collection.Iterator.foreach(Iterator.scala:943)
        at scala.collection.Iterator.foreach$(Iterator.scala:943)
        at scala.collection.AbstractIterator.foreach(Iterator.scala:1431)
        at scala.collection.IterableLike.foreach(IterableLike.scala:74)
        at scala.collection.IterableLike.foreach$(IterableLike.scala:73)
        at scala.collection.AbstractIterable.foreach(Iterable.scala:56)
        at scala.collection.TraversableLike.map(TraversableLike.scala:285)
        at scala.collection.TraversableLike.map$(TraversableLike.scala:278)
        at scala.collection.AbstractTraversable.map(Traversable.scala:108)
        at slick.jdbc.JdbcModelBuilder$TableBuilder.columns$lzycompute(JdbcModelBuilder.scala:162)
        at slick.jdbc.JdbcModelBuilder$TableBuilder.columns(JdbcModelBuilder.scala:162)
        at slick.jdbc.JdbcModelBuilder$TableBuilder.buildModel(JdbcModelBuilder.scala:160)
        at slick.jdbc.JdbcModelBuilder.$anonfun$buildModel$6(JdbcModelBuilder.scala:95)
        at scala.collection.TraversableLike.$anonfun$map$1(TraversableLike.scala:285)
        at scala.collection.Iterator.foreach(Iterator.scala:943)
        at scala.collection.Iterator.foreach$(Iterator.scala:943)
        at scala.collection.AbstractIterator.foreach(Iterator.scala:1431)
        at scala.collection.IterableLike.foreach(IterableLike.scala:74)
        at scala.collection.IterableLike.foreach$(IterableLike.scala:73)
        at scala.collection.AbstractIterable.foreach(Iterable.scala:56)
        at scala.collection.TraversableLike.map(TraversableLike.scala:285)
        at scala.collection.TraversableLike.map$(TraversableLike.scala:278)
        at scala.collection.AbstractTraversable.map(Traversable.scala:108)
        at slick.jdbc.JdbcModelBuilder.$anonfun$buildModel$4(JdbcModelBuilder.scala:95)
        at slick.dbio.DBIOAction.$anonfun$map$1(DBIOAction.scala:43)
        at slick.basic.BasicBackend$DatabaseDef.$anonfun$runInContextInline$1(BasicBackend.scala:172)
        at scala.concurrent.Future.$anonfun$flatMap$1(Future.scala:307)
        at scala.concurrent.impl.Promise.$anonfun$transformWith$1(Promise.scala:41)
        at scala.concurrent.impl.CallbackRunnable.run(Promise.scala:64)
        at java.util.concurrent.ForkJoinTask$RunnableExecuteAction.exec(ForkJoinTask.java:1402)
        at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)
        at java.util.concurrent.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1056)
        at java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1692)
        at java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:157)
[error] Nonzero exit code returned from runner: 1
[error] (slick-codegen / Compile / run) Nonzero exit code returned from runner: 1

```

## 推測できる原因

Slick の MySQLProfile.scala に定義されている
ColumnBuilder の DEFAULT を扱う箇所にバグらしきものがありました。

```scala
// (v,tpe) == ("NULL", "scala.math.BigDecimal") となってエラーが発生していました
override def default = meta.columnDef.map((_,tpe)).collect{
    case (v,"String")    => Some(Some(v))
    case ("1"|"b'1'", "Boolean") => Some(Some(true))
    case ("0"|"b'0'", "Boolean") => Some(Some(false))
    case ( v , "scala.math.BigDecimal") => Some( Some( scala.math.BigDecimal(v) ) )
}.getOrElse{
    val d = super.default
    if(meta.nullable == Some(true) && d == None){
        Some(None)
    } else d
}
override def length: Option[Int] = {
    val l = super.length
    if(tpe == "String" && varying && l == Some(65535)) None
    else l
}
```

`case ("NULL",_) => None`
を`collect`内の先頭に追加すれば問題を回避できる。

その回避策として１行追加するために Slick のコードをコピーして持ってきています。
そのために新たに追加したファイルは次の２つです。

- [CustomJdbcModelBuilder](CustomMySQLModelBuilder.scala)
- [CustomJdbcModelComponentExt](./CustomJdbcModelComponentExt.scala)

Slick 本体にバグ修正が取り込まれるように対応し、
それが取り込まれたあとはこのコードは不要になります。
なお、それらしい issue は見つかりませんでした。
Slick の issue は以下から確認できます。  
https://github.com/slick/slick/issues
