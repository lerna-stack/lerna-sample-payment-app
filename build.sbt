import Dependencies._

import scala.util.Try

lazy val `payment-app` = (project in file("."))
  .enablePlugins(JavaAppPackaging, JavaServerAppPackaging, RpmPlugin, SystemdPlugin)
  .aggregate(
    `presentation`,
    `gateway`,
    `adapter`,
    `application`,
    `read-model`,
    `utility`,
    `entrypoint`,
    `testkit`,
    `read-model-testkit`,
    `utility-testkit`,
    `external-system-testkit`,
  )
  .dependsOn(`presentation`, `gateway`, `adapter`, `application`, `read-model`, `utility`, `entrypoint`)
  .settings(
    inThisBuild(
      List(
        organization := "jp.co.tis.lerna.payment",
        version := "1.1.0",
        scalaVersion := "2.13.6",
        scalacOptions ++= Seq(
          "-deprecation",
          "-feature",
          "-unchecked",
          "-Xlint",
        ),
        scalacOptions ++= sys.env
          .get("lerna.enable.discipline").filter(_ == "true").map(_ => "-Xfatal-warnings").toSeq,
        javaOptions in run ++= distinctJavaOptions(
          sbtJavaOptions, // fork先にはシステムプロパティが引き継がれないため
          Seq(
            // ローカル開発環境でのみ有効にしたい環境変数はここで指定する。
            "-Djp.co.tis.lerna.payment.server-mode=DEV",
            """-Dlerna.util.encryption.base64-key="v5LCFG4V1CbJxxPg+WTd8w=="""",
            """-Dlerna.util.encryption.base64-iv="46A7peszgqN3q/ww4k8lWg=="""",
            "-Djp.co.tis.lerna.payment.presentation.util.api.default.BASE.active=on",
            "-Djp.co.tis.lerna.payment.application.persistence.cassandra.default.journal.keyspace-autocreate=true",
            "-Djp.co.tis.lerna.payment.application.persistence.cassandra.default.journal.tables-autocreate=true",
          ),
        ),
        fork in run := sys.props
          .get("fork").flatMap(s => Try(s.toBoolean).toOption).getOrElse(
            !isDebugging,
          ),                                    // CoordinatedShutdown の testのため・フォークするとデバッガが接続できなくなるため
        fork in Test := true,                   // ~test で繰り返しテストできるようにするため
        javaOptions in Test ++= sbtJavaOptions, // fork先にはシステムプロパティが引き継がれないため
        // forkプロセスのstdoutをこのプロセスのstdout,stderrをこのプロセスのstderrに転送する
        // デフォルトのLoggedOutputでは、airframeやkamonが標準エラーに出力するログが[error]とプリフィクスがつき、紛らわしいためです。
        outputStrategy := Some(StdoutOutput),
        resolvers += Resolver.sonatypeRepo("snapshots"),
      ),
    ),
    //
    // Packaging settings
    //
    name := sys.props.collectFirst { case ("project.name", v) => v }.getOrElse("lerna-sample-payment-app"),
    rpmRelease := "1",
    rpmVendor := "tis",
    maintainer in Linux := "TIS Inc.",
    rpmUrl := Some("http://www.tis.co.jp"),
    rpmLicense := Some("Apache License Version 2.0"),
    rpmAutoreq := "no",
    serverLoading in Rpm := Some(ServerLoader.Systemd),
    daemonUser := "payment-app",
    daemonGroup := "payment-app",
    retryTimeout in Rpm := 30,
    linuxPackageMappings in Rpm := configWithNoReplace((linuxPackageMappings in Rpm).value),
    linuxPackageSymlinks := Seq.empty,
    // インフラチームからの要請によりアプリ関連のファイルは全て /apl/ 配下に配置する
    defaultLinuxInstallLocation := "/apl",
    defaultLinuxLogsLocation := defaultLinuxInstallLocation.value + "/var/log",
    bashScriptEnvConfigLocation := Option(
      defaultLinuxInstallLocation.value + sys.props
        .collectFirst { case ("project.name", v) if v != "payment-app" => "/etc/default2" }.getOrElse("/etc/default"),
    ),
    linuxPackageMappings in Rpm := {
      val mappings = (linuxPackageMappings in Rpm).value
      mappings.map { linuxPackage =>
        val filtered = linuxPackage.mappings.filterNot {
          // PID ファイルは使わないので
          case (_, mappingName) => mappingName.startsWith("/var/run/")
        }
        linuxPackage.copy(mappings = filtered)
      } ++ Seq(
        packageMapping(
          (baseDirectory.value / "CHANGELOG.md") -> (defaultLinuxInstallLocation.value + s"/${name.value}" + "/CHANGELOG.md"),
        ),
        // lerna-terraform でモックサーバを起動するために使用する
        packageDirectoryAndContentsMapping(
          (baseDirectory.value / "docker/mock-server") -> (defaultLinuxInstallLocation.value + s"/${name.value}" + "/docker/mock-server"),
        ),
      )
    },
    rpmRelease := sys.props.collectFirst { case ("rpm.release", v) => v }.getOrElse("1"),
    rpmPrefix := Option(defaultLinuxInstallLocation.value),
    mainClass in Compile := Some("jp.co.tis.lerna.payment.entrypoint.Main"),
    javaOptions in Universal := Nil, // 注意： Terraform で application.ini を上書きするので javaOptions は定義しないこと。代わりに bashScriptExtraDefines を使う。
    bashScriptExtraDefines ++= Seq(
      s"""addJava "-Djp.co.tis.lerna.payment.presentation.versions.version=${version.value}"""",
      s"""addJava "-Djp.co.tis.lerna.payment.presentation.versions.commit-hash=${fetchGitCommitHash.value}"""",
    ),
    maintainerScripts in Rpm ++= {
      import RpmConstants._
      Map(
        Postun -> Seq(""), // デフォルトではサービスの再起動が行われるが、アップグレード後の任意のタイミングで再起動したいため
      )
    },
  )

lazy val `presentation` = (project in file("payment-app/presentation"))
  .dependsOn(
    `adapter`,
    `read-model`,
    `read-model-testkit` % "test",
    `utility`,
    `utility-testkit` % "test",
  )
  .settings(wartremoverSettings, coverageSettings)
  .settings(
    name := "presentation",
    libraryDependencies ++= Seq(
      Lerna.http,
      Lerna.validation,
      Lerna.management,
      Airframe.airframe,
      Akka.actorTyped,
      Akka.stream,
      AkkaHttp.http,
      AkkaHttp.sprayJson,
      Guava.guava,
      Akka.testKit         % Test,
      AkkaHttp.httpTestKit % Test,
    ),
  )

lazy val `gateway` = (project in file("payment-app/gateway"))
  .dependsOn(
    `adapter`,
    `utility`,
    `utility-testkit`         % "test",
    `external-system-testkit` % "test",
  )
  .settings(wartremoverSettings, coverageSettings)
  .settings(
    name := "gateway",
    libraryDependencies ++= Seq(
      Lerna.http,
      Airframe.airframe,
      Akka.actorTyped,
      Akka.stream,
      AkkaHttp.http,
      AkkaHttp.sprayJson,
      Akka.actorTestKitTyped % Test,
      AkkaHttp.httpTestKit   % Test,
    ),
  )

lazy val `adapter` = (project in file("payment-app/adapter"))
  .dependsOn(`utility`, `utility` % "test->test")
  .settings(wartremoverSettings, coverageSettings)
  .settings(
    name := "adapter",
    libraryDependencies ++= Seq(
      ScalaTest.scalaTest % Test,
    ),
  )

lazy val `application` = (project in file("payment-app/application"))
  .dependsOn(
    `adapter`,
    `read-model`,
    `read-model-testkit` % "test",
    `utility`,
    `utility-testkit` % "test",
  )
  .settings(wartremoverSettings, coverageSettings)
  .settings(
    name := "application",
    libraryDependencies ++= Seq(
      Lerna.utilSequence,
      Lerna.utilAkka,
      Lerna.management,
      Airframe.airframe,
      Akka.stream,
      Akka.persistenceTyped,
      Akka.clusterTyped,
      Akka.clusterTools,
      Akka.clusterShardingTyped,
      Akka.slf4j,
      Akka.persistenceQuery,
      AkkaPersistenceCassandra.akkaPersistenceCassandra,
      AkkaProjection.eventsourced,
      AkkaProjection.slick,
      Kryo.kryo,
      SprayJson.sprayJson,
      Akka.actorTestKitTyped % Test,
      Akka.multiNodeTestKit  % Test,
      Akka.streamTestKit     % Test,
    ),
  )

lazy val `read-model` = (project in file("payment-app/read-model"))
  .dependsOn(`utility`, `testkit` % "test")
  .settings(wartremoverSettings, coverageSettings)
  .settings(
    name := "read-model",
    libraryDependencies ++= Seq(
      Airframe.airframe,
      Slick.slick,
      Slick.hikaricp,
      MariaDB.connectorJ,
    ),
    scalacOptions ++= Seq(
      // 自動生成コードの警告を無視
      "-Wconf:cat=lint-multiarg-infix&src=scala/jp/co/tis/lerna/payment/readmodel/schema/Tables.scala:silent",
    ),
  )

lazy val `utility` = (project in file("payment-app/utility"))
  .dependsOn(`testkit` % "test")
  .settings(wartremoverSettings, coverageSettings)
  .settings(
    name := "utility",
    libraryDependencies ++= Seq(
      Lerna.util,
      Lerna.log,
      Airframe.airframe,
      Akka.slf4j, // Lerna.log が 古い akka-slf4j に依存しているため新しいversionで override
      // Logback and Janino is used for logging, utility has some logging configuration
      Logback.logback,
      Janino.janino,
      Expecty.expecty,
      ScalaTest.scalaTest % Test,
    ),
  )

lazy val `entrypoint` =
  (project in file("payment-app/entrypoint"))
    .dependsOn(
      `presentation`,
      `gateway`,
      `adapter`,
      `application`,
      `read-model`,
      `utility`,
      `testkit` % "test",
    )
    .settings(wartremoverSettings, coverageSettings)
    .settings(
      name := "entrypoint",
      libraryDependencies ++= Seq(
        Airframe.airframe,
        ScalaTest.scalaTest % Test,
      ),
    )

lazy val `testkit` = (project in file("payment-app/testkit"))
  .settings(wartremoverSettings, coverageSettings)
  .settings(
    name := "testkit",
    libraryDependencies ++= Seq(
      Lerna.util,
      Lerna.testkit,
      Expecty.expecty,
      ScalaTest.scalaTest,
      Airframe.airframe,
      WireMock.wireMock,
      Mockito.scalaTest,
    ),
  )

lazy val `read-model-testkit` = (project in file("payment-app/read-model-testkit"))
  .dependsOn(`testkit`, `read-model`)
  .settings(wartremoverSettings, coverageSettings)
  .settings(
    name := "read-model-testkit",
    libraryDependencies ++= Seq(
      Airframe.airframe,
      Slick.slick,
      Slick.hikaricp,
      H2.h2,
    ),
  )

lazy val `utility-testkit` = (project in file("payment-app/utility-testkit"))
  .dependsOn(`testkit`, `utility`)
  .settings(wartremoverSettings, coverageSettings)
  .settings(
    name := "utility-testkit",
    libraryDependencies ++= Seq(),
  )

lazy val `external-system-testkit` = (project in file("payment-app/external-system-testkit"))
  .dependsOn(`testkit`, `read-model-testkit` % "test")
  .settings(wartremoverSettings, coverageSettings)
  .settings(
    name := "external-system-testkit",
    libraryDependencies ++= Seq(
      Akka.actorTestKitTyped % Test,
      Akka.stream            % Test,
      AkkaHttp.http          % Test,
      AkkaHttp.httpTestKit   % Test,
      AkkaHttp.sprayJson     % Test,
    ),
  )

lazy val `example` =
  (project in file("payment-app/example"))
    .dependsOn(
      `presentation`,
      `gateway`,
      `adapter`,
      `application`,
      `read-model`,
      `read-model-testkit` % "test",
      `utility`,
      `utility-testkit` % "test",
    )
    .settings(wartremoverSettings, coverageSettings)
    .settings(
      name := "example",
      libraryDependencies ++= Seq(
        ScalaTest.scalaTest % Test,
      ),
    )

lazy val `slick-codegen` = (project in file("slick-codegen"))
  .settings(wartremoverSettings, coverageSettings)
  .settings(
    name := "slick-codegen",
    libraryDependencies ++= Seq(
      Slick.codegen,
      MariaDB.connectorJ,
    ),
  )

lazy val wart = project // FIXME: Plugin化
  .settings(
    libraryDependencies ++= Seq(
      Lerna.wartCore,
    ),
  )

lazy val wartremoverSettings = Def.settings(
  wartremoverClasspaths ++= {
    (fullClasspath in (wart, Compile)).value.map(_.data.toURI.toString)
  },
  // Warts.Unsafe をベースにカスタマイズ
  wartremoverErrors in (Compile, compile) := Seq(
    // Wart.Any,                        // Warts.Unsafe: Akka の API で Any が使われるため
    Wart.AsInstanceOf,            // Warts.Unsafe
    Wart.EitherProjectionPartial, // Warts.Unsafe
    Wart.IsInstanceOf,            // Warts.Unsafe
    // Wart.NonUnitStatements,          // Warts.Unsafe: 誤検知が多く、回避しようとすると煩雑なコードが必要になる
    Wart.Null,          // Warts.Unsafe
    Wart.OptionPartial, // Warts.Unsafe
    Wart.Product,       // Warts.Unsafe
    Wart.Return,        // Warts.Unsafe
    Wart.Serializable,  // Warts.Unsafe
    Wart.StringPlusAny, // Warts.Unsafe
    // Wart.Throw,                      // Warts.Unsafe: Future を失敗させるときに使うことがある
    Wart.TraversableOps,         // Warts.Unsafe
    Wart.TryPartial,             // Warts.Unsafe
    Wart.Var,                    // Warts.Unsafe
    Wart.ArrayEquals,            // Array の比較は sameElements を使う
    Wart.AnyVal,                 // 異なる型のオブジェクトを List などに入れない
    Wart.Equals,                 // == の代わりに === を使う
    Wart.ExplicitImplicitTypes,  // implicit val には明示的に型を指定する
    Wart.FinalCaseClass,         // case class は継承しない
    Wart.JavaConversions,        // scala.collection.JavaConverters を使う
    Wart.OptionPartial,          // Option#get は使わずに fold などの代替を使う
    Wart.Recursion,              // 単純な再帰処理は使わずに末尾最適化して @tailrec を付けるかループ処理を使う
    Wart.TraversableOps,         // head の代わりに headOption など例外を出さないメソッドを使う
    Wart.TryPartial,             // Success と Failure の両方をハンドリングする
    ContribWart.MissingOverride, // ミックスインしたトレイトと同じメソッドやプロパティを宣言するときは必ず override をつける
    ContribWart.OldTime,         // Java 8 の新しい Date API を使う
    ContribWart.SomeApply,       // Some(...) の代わりに Option(...) を使う
    CustomWart.Awaits,
    CustomWart.CyclomaticComplexity,
    CustomWart.NamingClass,
    CustomWart.NamingDef,
    CustomWart.NamingObject,
    CustomWart.NamingPackage,
    CustomWart.NamingVal,
    CustomWart.NamingVar,
  ),
  wartremoverErrors in (Test, compile) := (wartremoverErrors in (Compile, compile)).value,
  wartremoverErrors in (Test, compile) --= Seq(
    CustomWart.CyclomaticComplexity,
  ),
)

lazy val coverageSettings = Def.settings(
  coverageMinimum := 80,
  coverageFailOnMinimum := false,
  // You can exclude classes from being considered for coverage measurement by
  // providing semicolon-separated list of regular expressions.
  coverageExcludedPackages := Seq(
    """jp\.co\.tis\.lerna\.payment\.entrypoint\.Main\$?""",
    """jp\.co\.tis\.lerna\.payment\.readmodel\.schema\.Tables.*""",
  ).mkString(";"),
)

/** より上位のシステムプロパティを優先します
  */
def distinctJavaOptions(javaOptions: Seq[String]*): Seq[String] = {
  javaOptions.flatten
    .groupBy(_.split('=').head)
    .mapValues(_.head)
    .values.toSeq
}

def sbtJavaOptions: Seq[String] =
  sys.props
    .filterNot { case (key, _) => excludeSbtJavaOptions.exists(key.startsWith) }.map { case (k, v) => s"-D$k=$v" }.toSeq

// 前方一致で除外
lazy val excludeSbtJavaOptions = Set(
  "os.",
  "user.",
  "java.",
  "sun.",
  "awt.",
  "jline.",
  "jna.",
  "jnidispatch.",
  "sbt.",
)

lazy val isDebugging: Boolean = {
  import java.lang.management.ManagementFactory
  import scala.collection.JavaConverters._
  ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.exists(_.startsWith("-agentlib:jdwp="))
}

val fetchGitCommitHash = taskKey[String]("fetch git commit hash")
fetchGitCommitHash := {
  import scala.sys.process._
  "git rev-parse HEAD".!!.trim
}

addCommandAlias("take-test-coverage", "clean;coverage;test:compile;test;coverageReport;coverageAggregate;")
