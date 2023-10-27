# ClusterSharding の Entity を実装する際の注意点

本ドキュメントでは、Cluster Sharding を実装する際に注意が必要なポイントを解説する。
このドキュメントに記載されたポイントを考慮せずに実装をすると、障害の原因を埋め込んでしまう可能性がある。

## ◆ 後続の処理をブロックする処理のタイムアウト

`stash` を使って後続の処理を保留し、実行をブロックするタイプの処理では必ずタイムアウトを実装しなければならない。
タイムアウトを実装しない場合、後続の処理の実行がブロックされ続け、Entity が全く処理できない状態に陥る可能性がある。

以下の 4 つのポイントに注意した実装が必要。

1. 「処理中」に状態遷移する直前の `applyEvent` で `ProcessingTimeout` をスケジュールする
2. 状態遷移前にスケジュールされた `ProcessingTimeout` と同一のものだけをハンドリングする
3. `ProcessingTimeout` を受け取ったら「処理中」以外の状態に遷移する
4. 処理が完了したら `ProcessingTimeout` のスケジュールをキャンセルする

※「処理中」は後続の処理の実行を `stash` でブロックするタイプの Entity の状態のこと

それぞれのポイントを、PaymentActor の実装例を参考にしながら解説する。

PaymentActor は正常系では次のように状態遷移する（簡単のため、この説明ではキャンセルや異常系は無視している）。

**PaymentActor の代表的な状態遷移（正常系）：**
```scala
WaitingForRequest (initial) → Settling (processing) → Completed
```
- `WaitingForRequest`
    - クライアントからリクエストが来るのを待つ状態（初期状態）
- `Settling`
    - 外部システムからの応答を待つ状態（後続の要求を `stash`する「処理中」状態）
- `Completed`
    - 外部システムからの応答を受け取り、処理が完了した状態


### 1.「処理中」に状態遷移する直前の `applyEvent` で `ProcessingTimeout` をスケジュールする

「処理中」に状態遷移する直前に呼ばれる `applyEvent` で `ProcessingTimeout` を `startSingleTimer` に設定する。
こうすることにより「処理中」状態である `Settling` で、一定時間後に `ProcessingTimeout` を受信できるようになる。

> ```scala
> override def applyEvent(event: SettlementAccepted)(implicit setup: Setup): State =
>   event match {
>     case event: SettlementAccepted =>
>        ... 略 ...
>        val processingTimeoutMessage: ProcessingTimeout =
>          ProcessingTimeout(event.systemTime, setup.askTimeout, setup.context.system.settings.config)
> 
>        setup.timers.startSingleTimer( 👈
>          msg = processingTimeoutMessage,
>          delay = processingTimeoutMessage.timeLeft(setup.dateTimeFactory),
>        )
>       Settling(
>         ... 略 ...
>       )
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L260-L278)**

`startSingleTimer` の `delay` には `timeLeft` した値を設定する。
これにより、`ProcessingTimeout` が生成されたタイミングを考慮して、タイムアウトを発生させるまで待つ期間が設定される。

---

**補足：なぜ、タイムアウトの期間を直接設定せず、`timeLeft` の期間を設定するのか？**

Entity が「処理中」で復元された場合に、即座にタイムアウトを発動させるため。

Entity が「処理中」状態のときに突然停止してしまったようなケースでは、復元後の状態が「処理中」になってしまう。
この状態のままでは新しい要求を処理できないため、タイムアウトを発生させて「処理中」状態から離脱する必要がある。

`applyEvent` は通常の処理で Entity の状態を変更する目的で呼ばれるが、Entity の復元処理中に Entity の状態を変更する目的でも呼ばれる。
復元処理中にも `startSingleTimer` が呼ばれ、過去の `ProcessingTimeout` が再スケジュールされる。
このとき、`ProcessingTimeout#timeLeft` を用いることで、`ProcessingTimeout` が生成されたタイミングを考慮して、
タイムアウトを発生させるまでの期間が設定されるため、十分時間が経過している場合に設定される `delay` は `0s` になる。
（即座にタイムアウトが発動する）

例：
- `ProcessingTimeout` 生成時間: 1/1 17:00:00
- タイムアウト時間: 15s

のとき、システム日時が `1/1 17:00:01` のときに `timeLeft` した結果は `14s` になる。
`1/1 18:00:00` のときに `timeLeft` した結果は経過時間が考慮され `0s` になる。

---

### 2. 状態遷移前にスケジュールされた `ProcessingTimeout` と同一のものだけをハンドリングする

「処理中」状態である `Settling` では、状態遷移直前に発行された `ProcessingTimeout` のみをハンドリングし、
それ以外の `ProcessingTimeout` を無視する必要がある。
復元処理中にスケジュールされる、過去の `ProcessingTimeout` が「それ以外の `ProcessingTimeout`」にあたる。

もし無条件に `ProcessingTimeout` をハンドリングすると、誤ったタイミングでタイムアウトが発生し、
処理が予期せず中断してしまう可能性がある。

PaymentActor の例では、次のように実装している。
パターンマッチにおいて \`...\`（バッククオート）で変数を囲った場合、その変数と同一のものだった場合にマッチするという意味になる（Scala の機能）。
つまり、`cmd == processingTimeoutMessage` だった場合にマッチする。

> ```scala
> override def applyCommand(cmd: Command)(implicit setup: Setup): ReplyEffect[SettlingResult] = cmd match {
>   ... 略 ...
>   case `processingTimeoutMessage` =>  👈
>     import processingTimeoutMessage.requestContext
>     setup.logger.info("処理タイムアウトしました: {}", processingTimeoutMessage)
>     Effect
>       .persist(SettlementTimeoutDetected()(requestContext.traceId))
>       .thenRun((_: State) => stopSelfSafely())
>       .thenNoReply()
>       .thenUnstashAll()
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L516-L523)**

この `processingTimeoutMessage` は `Settling` のプロパティとして定義されており、
ここには状態遷移直前にスケジュールされた `ProcessingTimeout` を設定する。

**プロパティ定義：**
> ```scala
>  final case class Settling(
>      requestInfo: Settle,
>      systemTime: LocalDateTime,
>      processingTimeoutMessage: ProcessingTimeout, 👈
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L386-L389)**

**状態遷移時に `ProcessingTimeout` を設定：**
> ```scala
> val processingTimeoutMessage: ProcessingTimeout =
>   ProcessingTimeout(event.systemTime, setup.askTimeout, setup.context.system.settings.config)
>
> setup.timers.startSingleTimer(
>   msg = processingTimeoutMessage,
>   delay = processingTimeoutMessage.timeLeft(setup.dateTimeFactory),
> )
>
> Settling(
>   ... 略 ...
>   processingTimeoutMessage, 👈
> )
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L266-L278)**

### 3. `ProcessingTimeout` を受け取ったら「処理中」以外の状態に遷移する

`ProcessingTimeout` をハンドリングした場合、その状態に留まることなく他の状態に遷移する必要がある。
遷移しないと、タイムアウトしても引き続き後続の処理の実行がブロックされてしまうため。

PaymentActor の例では、`ProcessingTimeout` をハンドリングし、`SettlementTimeoutDetected` イベントを発行することで
`Failed` 状態に遷移している。

> ```scala
> case `processingTimeoutMessage` => 
>   import processingTimeoutMessage.requestContext
>   setup.logger.info("処理タイムアウトしました: {}", processingTimeoutMessage)
>   Effect
>     .persist(SettlementTimeoutDetected()(requestContext.traceId)) 👈
>     .thenRun((_: State) => stopSelfSafely())
>     .thenNoReply()
>     .thenUnstashAll()
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L516-L523)**

> ```scala
>  final case class Settling(
>      ... 略 ...
>  ) extends StateBase[SettlingResult] {
>    override def applyEvent(event: SettlingResult)(implicit setup: Setup): State =
>         ... 略 ...
>         case _: SettlementTimeoutDetected =>
>           val message = UnpredictableError()
>           Failed(message)  👈
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L412-L414)**

### 4. 処理が完了したら `ProcessingTimeout` のスケジュールをキャンセルする

「処理中」で実行していた処理が成功したり、失敗したり結果が明らかになったタイミングで `ProcessingTimeout` は不要になる。
しかし、一度 `startSingleTimer` に設定されたタイマーは発動するか、キャンセルするまで有効なままなため、
`ProcessingTimeout` が不要になったタイミングでキャンセルする必要がある。

キャンセルしないと、無駄なリソース消費が発生したり、`ProcessingTimeout` が処理されずに無視されたことを通知する無駄なログが出力されたりする。

PaymentActor の例では、処理結果である `SettlementResult` を受け取ったタイミングで `setup.timers.cancel` を実行しタイマーをキャンセルしている。

> ```scala
>  override def applyCommand(cmd: Command)(implicit setup: Setup): ReplyEffect[SettlingResult] = cmd match {
>    case paymentResult: SettlementResult =>
>      ... 略 ...
>      setup.timers.cancel(processingTimeoutMessage)  👈
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L426-L429)**


## ◆ Entity の休止

Entity はインメモリで起動し、何もしなければ起動し続ける。
全ての Entity が起動し続けると、メモリが不足してしまうため新たなコマンドが来る可能性が低くなったタイミングで Entity を休止する。

休止すべき状況は、以下のようなケースが考えられる。
- 一定時間コマンドが来なかった場合
- ある処理が完了し、それ以降新たなコマンドが来る可能性が低い

メモリ不足を回避するため、「一定時間コマンドが来なかった場合」に Entity を休止する実装は一律で実装しておいたほうが良い。
「ある処理が完了し、それ以降新たなコマンドが来る可能性が低い」ときに Entity を休止する実装を追加すると、さらにメモリを節約できる。

一定時間コマンドが来なかった場合にメッセージを送るには、`EntityContext#setReceiveTimeout` を実行する。

PaymentActor の例では、Entity の生成時に実行している。

> ```scala
> private[actor] def apply(
>     ... 略 ...
> ): Behavior[Command] = {
>   Behaviors.setup { context =>
>      ... 略 ...
>         val receiveTimeout: time.Duration =
>           setup.context.system.settings.config
>             .getDuration("jp.co.tis.lerna.payment.application.ecpayment.issuing.actor.receive-timeout")
>        setup.context.setReceiveTimeout(receiveTimeout.asScala, ReceiveTimeout)  👈
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L213-L217)**

一定時間コマンドが来ないと、`setReceiveTimeout` に指定された `ReceiveTimeout` メッセージが Entity に自動で送られる。

PaymentActor の例では、各状態で `ReceiveTimeout` をハンドリングし、`handleReceiveTimeout` を呼び出している。
この処理の中では `EntityContext#shard` に対して `ClusterSharding.Passivate` メッセージを送っている。

> ```scala
>  override def applyCommand(cmd: Command)(implicit setup: Setup): ReplyEffect[SettlementAccepted] = cmd match {
>    ... 略 ...
>    case ReceiveTimeout          => handleReceiveTimeout() 👈
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L328)**

> ```scala
> private def handleReceiveTimeout[Event]()(implicit setup: Setup): ReplyEffect[Event] = {
>   implicit val appRequestContext: AppRequestContext = AppRequestContext(TraceId.unknown, setup.tenant)
>   setup.logger.info("Actorの生成から一定時間経過しました。Actorを停止します。")
>   stopSelfSafely()
>   Effect.noReply
> }
> private def stopSelfSafely()(implicit setup: Setup): Unit = {
>   setup.entityContext.shard ! ClusterSharding.Passivate(setup.context.self) 👈
> }
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L1007-L1012)**

`EntityContext#shard` に対して `ClusterSharding.Passivate` メッセージを送ると、Entity のグレースフルシャットダウンが実行され、
最終的に Entity が休止する（メモリが開放される）。
Entity のグレースフルシャットダウンについては、後続のセクションで解説する。

## ◆ Entity のグレースフルシャットダウン

外部システムとの整合性を維持するため、外部システムの処理中は Entity を停止せずに外部システムのレスポンスを受け取ってから停止する必要がある。
一方で Entity はあらゆる状態で停止リクエストを受け取る可能性がある。
Entity で停止リクエストを受け取ったときに常に即時停止するのではなく、外部システムのレスポンスを待っている間は Entity の停止を保留する必要がある。


停止リクエストは `StopMessage` として Entity に設定したコマンドを使って通知される。

**StopMessage の設定例：**
> ```scala
> Entity(EntityTypeKey[Command](ActorPrefix.Ec.houseMoney))(createBehavior = entityContext => {
>     PaymentActor(
>       ... 略 ...
>     )
> })
> .withStopMessage(StopActor), 👈
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L171-L182)**

アプリケーションの処理状態にかかわらず、次のような事象が起きると Entity は停止リクエスト（`StopMessage`）を受け取る。

- ノード（アプリケーションプロセス）のシャットダウン
- Shard のリバランス
    - サーバーの負荷を均一にするため自動的に行われる処理。
      ノードの停止や起動によってノード数が変動し、負荷が不均一になったときに発生。

アプリケーションのローリングアップデート（無停止リリース）は、アプリケーションの停止と起動の繰り返し操作のため、
`StopMessage` が多く発生することになる。

### `StopMessage` を保留してレスポンスが得られた後に Entity を停止する実装例

PaymentActor では `StopActor` コマンドが StopMessage として設定されている。

> ```scala
> .withStopMessage(StopActor),
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L171-L182)**


PaymentActor は正常系では次のように状態遷移する（簡単のため、この説明ではキャンセルや異常系は省略する）。

**PaymentActor の代表的な状態遷移（正常系）：**
```scala
WaitingForRequest (initial) → Settling → Completed
```
- `WaitingForRequest`
    - クライアントからリクエストが来るのを待つ状態（初期状態）
- `Settling`
    - 外部システムからの応答を待つ状態
- `Completed`
    - 外部システムからの応答を受け取り、処理が完了した状態

`WaitingForRequest` や `Completed` といった状態では外部システムが処理を行っていないため、次のように `Effect.stop()` を使って即座に Entity の Actor を停止させる。

> ```scala
> case StopActor               => Effect.stop().thenNoReply()
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L327)**

外部システムにリクエストを送信すると `Settling` に状態遷移する。
この状態では外部システムが処理中であり、処理結果を受け取る必要があるため、次のように  `Effect.stash()` を使って `StopActor` の処理を保留する。

> ```scala
> case StopActor =>
>   implicit def tenant: AppTenant = setup.tenant // `import setup.tenant` だと型推論がうまく動かないため def で型を明示
>   import lerna.util.tenant.TenantComponentLogContext.logContext
>   setup.logger.info(s"[state: ${this.toString}, receive: StopActor] 処理結果待ちのため終了処理を保留します")
>   Effect.stash()
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L420-L424)**

保留された `StopActor` は、`Settling` で外部システムからの応答を受け取ったときに `Effect.thenUnstashAll()` を呼ぶことで処理可能になる。
`Effect.thenUnstashAll()` によって、以前に `Effect.stash()` で保留されたコマンドは全て Entity のメールボックス（処理待ちのコマンドを貯めるキュー）に戻り、順次処理される。

> ```scala
>  case paymentResult: SettlementResult =>
>    import paymentResult.{ appRequestContext, processingContext }
>    setup.timers.cancel(processingTimeoutMessage)
>    paymentResult.result match {
>      case Right((payCredential, req, result)) =>
>        result match {
>          case Right(response) =>
>            response.rErrcode match {
>              case `errCodeOk` =>
>                ... 略 ...
>                Effect
>                  .persist(event)
>                  .thenReply(processingContext.replyTo)((_: State) => res)
>                  .thenUnstashAll() 👈
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L426-L457)**

PaymentActor は `Settling` 状態を終えると、`Completed` 状態に遷移するため、
`Effect.thenUnstashAll()` によって戻された `StopActor` は `Completed` で処理される。

> ```scala
>  final case class Completed(
>     ... 略 ...
>  ) extends StateBase[CancelAccepted] {
>   ... 略 ...
>   override def applyCommand(cmd: Command)(implicit setup: Setup): ReplyEffect[CancelAccepted] = cmd match {
>     ... 略 ...
>     case StopActor               => Effect.stop().thenNoReply() 👈
> ```
> **[application/ecpayment/issuing/actor/PaymentActor.scala](https://github.com/lerna-stack/lerna-sample-payment-app/blob/v2022.3.0/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ecpayment/issuing/actor/PaymentActor.scala#L601)**
