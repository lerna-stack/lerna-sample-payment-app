# ReadModelUpdater用タグ付け

ReadModelUpdaterで処理させるため、Eventにタグを付与する必要がある。

## 関連

- [Persistence Query • Akka Documentation](https://doc.akka.io/docs/akka/current/persistence-query.html)
- [application/readmodelupdater/tagging/TaggingEventAdapter.scala](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/readmodelupdater/tagging/TaggingEventAdapter.scala)

## Event にタグを付与する方法

1. [EventToTags](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/readmodelupdater/tagging/EventToTags.scala) を継承した `Event -> tags` 変換ルールを定義する
   
   参考: [SalesDetailEventToTags](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/readmodelupdater/tagging/salesdetail/SalesDetailEventToTags.scala)
 
1.  [TaggingEventAdapter](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/readmodelupdater/tagging/TaggingEventAdapter.scala) の `eventToTagsSet` に 1 で作成した ルールobject を追加

1. [applicationのreference.conf](/payment-app/application/src/main/resources/reference.conf) の `event-adapter-bindings` に 設定を追加
    
    ※ 他のRMUで `readmodelupdater-tagging` が紐付けされていても、個別に定義する（機能削除時に影響がないようにするため）
    ```conf
    "(対象classのFQCN)" = readmodelupdater-tagging
    ```

## 備考

### EventToTags を使う理由

EventAdapter は 1 class ごとに 1 つしか使われないため

参考: [akka/EventAdapters.scala at v2.6.8 · akka/akka](https://github.com/akka/akka/blob/v2.6.8/akka-persistence/src/main/scala/akka/persistence/journal/EventAdapters.scala#L30-L53)

例) 以下の設定だと、どちらか片方しかタグが付与されない

※ `IssuingServiceSettlementNotificationEvent` は `SalesDetailDomainEvent` を継承している

```hocon
event-adapters {
    salesdetail-tagging     = "jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.TaggingEventAdapter"
    incentivecharge-tagging = "jp.co.tis.lerna.payment.application.readmodelupdater.incentivecharge.TaggingEventAdapter"
}
event-adapter-bindings {
    "jp.co.tis.lerna.payment.application.readmodelupdater.salesdetail.model.SalesDetailDomainEvent"                       = salesdetail-tagging
    "jp.co.tis.lerna.payment.application.paymentlog.issuing.notification.actor.IssuingServiceSettlementNotificationEvent" = incentivecharge-tagging
}
```

※ この問題に対処しつつ保守性を高めるために、共通の `readmodelupdater-tagging` を定義して、 [EventToTags](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/readmodelupdater/tagging/EventToTags.scala) を複数定義できるようにした
