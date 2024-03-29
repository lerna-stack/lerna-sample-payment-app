import sbt._

object Dependencies {

  object Versions {
    val lerna                    = "3.0.1"
    val akka                     = "2.6.10"
    val akkaHttp                 = "10.2.4"
    val akkaPersistenceCassandra = "1.0.1"
    val akkaProjection           = "1.1.0"
    val scalaTest                = "3.1.4"
    val airframe                 = "20.9.0"
    val logback                  = "1.2.3"
    val slick                    = "3.3.2"
    val expecty                  = "0.14.1"
    val janino                   = "3.0.16"
    val kryo                     = "1.1.5"
    val h2                       = "1.4.200"
    val wireMock                 = "2.30.1"
    val guava                    = "29.0-jre"
    val sprayJson                = "1.3.5"
    val mariadbConnectorJ        = "2.6.2"
  }

  object Lerna {
    val http         = "com.lerna-stack" %% "lerna-http"          % Versions.lerna
    val log          = "com.lerna-stack" %% "lerna-log"           % Versions.lerna
    val management   = "com.lerna-stack" %% "lerna-management"    % Versions.lerna
    val testkit      = "com.lerna-stack" %% "lerna-testkit"       % Versions.lerna
    val util         = "com.lerna-stack" %% "lerna-util"          % Versions.lerna
    val utilAkka     = "com.lerna-stack" %% "lerna-util-akka"     % Versions.lerna
    val utilSequence = "com.lerna-stack" %% "lerna-util-sequence" % Versions.lerna
    val validation   = "com.lerna-stack" %% "lerna-validation"    % Versions.lerna
    val wartCore     = "com.lerna-stack" %% "lerna-wart-core"     % Versions.lerna
  }

  object Akka {
    val slf4j                = "com.typesafe.akka" %% "akka-slf4j"                  % Versions.akka
    val actorTyped           = "com.typesafe.akka" %% "akka-actor-typed"            % Versions.akka
    val stream               = "com.typesafe.akka" %% "akka-stream"                 % Versions.akka
    val clusterTyped         = "com.typesafe.akka" %% "akka-cluster-typed"          % Versions.akka
    val clusterTools         = "com.typesafe.akka" %% "akka-cluster-tools"          % Versions.akka
    val clusterShardingTyped = "com.typesafe.akka" %% "akka-cluster-sharding-typed" % Versions.akka
    val persistenceTyped     = "com.typesafe.akka" %% "akka-persistence-typed"      % Versions.akka
    val persistenceQuery     = "com.typesafe.akka" %% "akka-persistence-query"      % Versions.akka
    val testKit              = "com.typesafe.akka" %% "akka-testkit"                % Versions.akka
    val actorTestKitTyped    = "com.typesafe.akka" %% "akka-actor-testkit-typed"    % Versions.akka
    val streamTestKit        = "com.typesafe.akka" %% "akka-stream-testkit"         % Versions.akka
    val multiNodeTestKit     = "com.typesafe.akka" %% "akka-multi-node-testkit"     % Versions.akka
  }

  object AkkaHttp {
    val http        = "com.typesafe.akka" %% "akka-http"            % Versions.akkaHttp
    val sprayJson   = "com.typesafe.akka" %% "akka-http-spray-json" % Versions.akkaHttp
    val httpTestKit = "com.typesafe.akka" %% "akka-http-testkit"    % Versions.akkaHttp
  }

  object AkkaPersistenceCassandra {
    val akkaPersistenceCassandra =
      "com.typesafe.akka" %% "akka-persistence-cassandra" % Versions.akkaPersistenceCassandra
  }

  object AkkaProjection {
    val eventsourced = "com.lightbend.akka" %% "akka-projection-eventsourced" % Versions.akkaProjection
    val slick        = "com.lightbend.akka" %% "akka-projection-slick"        % Versions.akkaProjection
  }

  object ScalaTest {
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest
  }

  object Airframe {
    val airframe = "org.wvlet.airframe" %% "airframe" % Versions.airframe
  }

  object SprayJson {
    val sprayJson = "io.spray" %% "spray-json" % Versions.sprayJson
  }

  object Logback {
    val logback = "ch.qos.logback" % "logback-classic" % Versions.logback
  }

  object Slick {
    val slick    = "com.typesafe.slick" %% "slick"          % Versions.slick
    val codegen  = "com.typesafe.slick" %% "slick-codegen"  % Versions.slick
    val hikaricp = "com.typesafe.slick" %% "slick-hikaricp" % Versions.slick
  }

  object Expecty {
    val expecty = "com.eed3si9n.expecty" %% "expecty" % Versions.expecty
  }

  object Janino {
    val janino = "org.codehaus.janino" % "janino" % Versions.janino
  }

  object Kryo {
    val kryo = "io.altoo" %% "akka-kryo-serialization" % Versions.kryo
  }

  object H2 {
    val h2 = "com.h2database" % "h2" % Versions.h2
  }

  object Mockito {
    val scalaTest = "org.mockito" %% "mockito-scala-scalatest" % "1.7.1"
  }

  object WireMock {
    val wireMock = "com.github.tomakehurst" % "wiremock-jre8" % Versions.wireMock
  }

  object Guava {
    val guava = "com.google.guava" % "guava" % Versions.guava
  }

  object MariaDB {
    val connectorJ = "org.mariadb.jdbc" % "mariadb-java-client" % Versions.mariadbConnectorJ
  }

}
