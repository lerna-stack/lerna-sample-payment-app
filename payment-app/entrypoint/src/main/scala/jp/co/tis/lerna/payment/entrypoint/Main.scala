package jp.co.tis.lerna.payment.entrypoint

import akka.actor.{ ActorSystem, CoordinatedShutdown }
import akka.cluster.Cluster
import com.typesafe.config.ConfigFactory
import lerna.log.AppLogging
import lerna.util.encryption.EncryptionConfig
import wvlet.airframe._

import scala.concurrent.Future
import scala.util.Failure

// CI may fail when there is a warning such as 'unused import'
import scala.util.Either

@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
    "lerna.warts.Awaits",
  ),
)
object Main extends App with AppLogging {
  import lerna.log.SystemComponentLogContext.logContext

  private val config = ConfigFactory.load()

  val validationErrors = Seq(
    EncryptionConfig(config).validate(),
  ).collect {
    case Failure(exception) => exception
  }

  if (validationErrors.nonEmpty) {
    validationErrors.foreach { throwable =>
      logger.error(throwable, "起動時バリデーションエラー")
    }
    System.exit(1)
  }

  private val system: ActorSystem = ActorSystem("GatewaySystem", config)
  logger.info("ActorSystem({})起動完了", system)

  val cluster = Cluster(system)
  logger.info("Akka Clusterへの参加待機中: {}", cluster.state)

  cluster.registerOnMemberUp {
    logger.info("Akka Clusterへの参加完了: {}", cluster.state)

    val serverMode = config.getString("jp.co.tis.lerna.payment.server-mode")
    val design: Design = serverMode match {
      case "PRODUCTION" => DIDesign.design(system).withProductionMode
      case "DEV"        => DIDesign.design(system)
      case _            => throw new IllegalStateException(s"Illegal server-mode: $serverMode")
    }

    val session = design.newSessionBuilder.noShutdownHook.create
    session.start

    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseServiceStop, taskName = "shutdown-airframe-session") {
      () =>
        logger.info(s"終了処理のため、airframe session を shutdown します")
        session.shutdown

        Future.successful(akka.Done)
    }

    session.build[PaymentApp].start()
  }
}
