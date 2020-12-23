package jp.co.tis.lerna.payment.application.util.health

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import akka.Done
import akka.actor.{ Actor, ActorSystem, CoordinatedShutdown, NoSerializationVerificationNeeded, Props }
import akka.pattern.ask
import akka.persistence.PersistentActor
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import akka.util.Timeout
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.adapter.util.health.HealthCheckApplication
import jp.co.tis.lerna.payment.application.util.tenant.actor.MultiTenantPersistentSupport
import jp.co.tis.lerna.payment.readmodel.JDBCService
import jp.co.tis.lerna.payment.readmodel.schema.Tables
import jp.co.tis.lerna.payment.utility.tenant.AppTenant
import lerna.log.AppLogging
import lerna.util.time.JavaDurationConverters._

import scala.concurrent.Future
import scala.util.Failure

object HealthCheckApplicationImpl {

  /** Akka Persistence Cassandra の HealthCheck が journal plugin の変更をまだサポートしていないため
    */
  private object AkkaPersistenceCassandraHelper extends AppLogging {
    private case object Ping extends NoSerializationVerificationNeeded

    /** 初期化順の関係で autoCreateTable などの AkkaPersistenceCassandra のセッション初期化処理が実行されないことがある問題に対処する
      */
    def initializeAkkaPersistenceCassandra(system: ActorSystem)(implicit _tenant: AppTenant): Future[Done] = {
      val props = Props(new PersistentActor with MultiTenantPersistentSupport {
        override def receiveRecover: Receive = Actor.emptyBehavior

        override def receiveCommand: Receive = {
          case Ping =>
            // Cassandraからの読み込み・Recovery が完了したら receiveCommand にメッセージが渡される
            sender() ! Done
            context.stop(self)
        }

        override def persistenceId: String = s"dummy-${UUID.randomUUID()}"

        // `def journalPluginId: String` は MultiTenantPersistentSupport が `tenant` をもとに自動生成する
        override implicit def tenant: AppTenant = _tenant
      })
      val actor = system.actorOf(props)

      implicit val timeout: Timeout = system.settings.config
        .getDuration(
          "jp.co.tis.lerna.payment.application.util.health.wait-for-akka-persistence-cassandra-initialization",
        ).asScala

      import system.dispatcher
      (actor ? Ping).mapTo[Done] andThen {
        case Failure(exception) =>
          import lerna.util.tenant.TenantComponentLogContext.logContext
          logger.error(exception, "Cassandraへの接続/初期化に失敗しました。アプリを再起動してください。")
      }
    }
  }
}

class HealthCheckApplicationImpl(config: Config, tables: Tables, system: ActorSystem, jdbcService: JDBCService)
    extends HealthCheckApplication
    with AppLogging {

  import system.dispatcher

  private[this] val sessionMap = AppTenant.values.map { implicit tenant =>
    /* 1. CassandraSessionRegistry(system).sessionFor は config keyごとに session を共有している
     * 2. オプションとしてsession作成時の初期化処理を設定できる
     * 3. Akka Persistence Cassandra 側には session作成時の初期化処理(keyspace作成など)が存在するが、ヘルスチェック側が先にセッションを作成すると、スキップされてしまう
     * 4. Akka Persistence Cassandra 側の初期化処理を確実にするため PersistentActor を作成して journal の作成を間接的に行なう (すべて `private[akka]` なので外部からアクセス不可能)
     */
    import HealthCheckApplicationImpl.AkkaPersistenceCassandraHelper.initializeAkkaPersistenceCassandra
    tenant -> initializeAkkaPersistenceCassandra(system).map { _ =>
      // cassandra 固定のためハードコードにする
      val pluginIdPrefix = "jp.co.tis.lerna.payment.application.persistence.cassandra"
      CassandraSessionRegistry(system).sessionFor(s"${pluginIdPrefix}.tenants.${tenant.id}")
    }
  }.toMap

  val isActive = new AtomicBoolean(true)

  /** ヘルスチェックを止めて常に失敗するようにする<br>
    * Graceful Shutdown用
    */
  override def kill(): Unit = {
    isActive.set(false)

    import lerna.log.SystemComponentLogContext.logContext
    logger.info("ヘルスチェックを停止しました。今後、常に NG が返ります。")
  }

  override def healthy()(implicit tenant: AppTenant): Future[Done] = {

    if (isActive.get()) {
      val checkingCassandra = checkCassandra()
      val checkingRDBMS     = checkRDBMS()

      for {
        _ <- checkingCassandra
        _ <- checkingRDBMS
      } yield Done
    } else {
      Future.failed(new RuntimeException("ヘルスチェック機能は停止されています"))
    }
  }

  /** ヘルスチェック。
    * Cassandra に何らかの障害が発生していたら Future が fail になることを想定している。
    * @return Future[Done]
    */
  def checkCassandra()(implicit tenant: AppTenant): Future[Done] = {
    import lerna.util.tenant.TenantComponentLogContext.logContext
    logger.debug("ヘルスチェックcassandra")
    (for {
      /*
       Akka Persistence Cassandra の HealthCheck が journal plugin の動的変更をサポートしていないため、
       セッションを自前で作成して、マルチテナント対応の Cassandra ヘルスチェックをする
       FIXME: Akka Persistence Cassandra の HealthCheck が journal plugin の動的変更をサポートしたら実装を整理する
       */
      session <- sessionMap(tenant)
      result  <- session.selectOne("SELECT release_version FROM system.local;")
    } yield {
      val value = result.map { _.getString(0) }.getOrElse("")
      logger.debug("cassandra release_version:" + value)
      if (value.nonEmpty) {
        Done
      } else {
        throw new IllegalStateException(s"Illegal result: $value")
      }
    }).recover {
      case exception: Exception =>
        logger.warn(exception, "Cassandra が利用できません")
        throw new IllegalStateException("Cassandra Health Check Failed", exception)
    }
  }

  /** ヘルスチェック。
    * RDBMS に何らかの障害が発生していたら Future が fail になることを想定している。
    * @return Future[Done]
    */
  def checkRDBMS()(implicit tenant: AppTenant): Future[Done] = {
    import tables.profile.api._
    import lerna.util.tenant.TenantComponentLogContext.logContext
    logger.debug("ヘルスチェックRDBMS")
    val action = sql"""SELECT 1 FROM DUAL""".as[Boolean].head
    jdbcService.db
      .run(action).map { result =>
        if (result) {
          Done
        } else {
          throw new IllegalStateException(s"Illegal result: $result")
        }
      }.recover {
        case exception: Exception =>
          logger.warn(exception, "RDBMS が利用できません")
          throw new IllegalStateException(s"RDBMS Health Check Failed", exception)
      }
  }

  CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, taskName = "stop-health-check") {
    () =>
      import lerna.log.SystemComponentLogContext.logContext
      if (isActive.get()) {
        logger.info("終了処理のため、ヘルスチェック機能を停止します")
        kill()

        Future {
          import scala.concurrent.blocking
          blocking {
            val finiteDuration =
              config.getDuration("jp.co.tis.lerna.payment.application.util.health.wait-for-detaching").asScala

            logger.info(s"終了処理のため、ヘルスチェック停止後、切り離されるまで $finiteDuration 待ちます")
            Thread.sleep(finiteDuration.toMillis)

            Done
          }
        }
      } else {
        Future.successful(Done)
      }
  }

}
