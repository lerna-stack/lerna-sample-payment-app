## to 0.x

### ネームスペース変更
`lerna.???` にネームスペースが変更されました。  
コンパイルエラーになる `import` を変更してください。  
ほとんどの場合、`import`を削除し、IDEの自動検出で解決できます。

### `HttpRequestProxySupport` を使っている場合
設定ファイル (`*.conf`) の該当項目名を変更してください。

| old | new |
| --- | --- |
| jp.co.tis.lerna.payment.gateway.global.proxy.host | lerna.http.proxy.tenants.${tenant.id}.host |
| jp.co.tis.lerna.payment.gateway.global.proxy.port | lerna.http.proxy.tenants.${tenant.id}.port |
| jp.co.tis.lerna.payment.gateway.global.proxy.authentication.username | lerna.http.proxy.tenants.${tenant.id}.authentication.username |
| jp.co.tis.lerna.payment.gateway.global.proxy.authentication.password | lerna.http.proxy.tenants.${tenant.id}.authentication.password |

### `logback` の `conversionRule` を使っている場合
次のFQCNを新しいものに変更してください。

| old | new |
| --- | --- |
| `jp.co.tis.lerna.payment.utility.log.converter.OneLineEventConverter` | `lerna.log.logback.converter.OneLineEventConverter` |
| `jp.co.tis.lerna.payment.utility.log.converter.OneLineExtendedStackTraceConverter` | `lerna.log.logback.converter.OneLineExtendedStackTraceConverter` |

また、依存ライブラリに `logback-classic` を追加してください。
```sbt
"ch.qos.logback" % "logback-classic" % "1.2.3"
```

### `encryption` を使っている場合
設定ファイル (`*.conf`) の該当項目名を変更してください。

| old | new |
| --- | --- |
| `jp.co.tis.lerna.payment.utility.encryption.base64-key` | `lerna.util.encryption.base64-key` |
| `jp.co.tis.lerna.payment.utility.encryption.base64-iv` | `lerna.util.encryption.base64-iv` |

### `processing-timeout` を使っている場合

設定ファイル (`*.conf`) の該当項目名を変更してください。

| old | new |
| --- | --- |
| `jp.co.tis.lerna.payment.application.util.processing-timeout.fail-safe-margin` | `lerna.util.akka.processing-timeout.fail-safe-margin` |

### `at-least-once-delivery` を使っている場合

設定ファイル (`*.conf`) の該当項目名を変更してください。  

| old | new |
| --- | --- |
| `jp.co.tis.lerna.payment.application.util.at-least-once-delivery.redeliver-interval` | `lerna.util.akka.at-least-once-delivery.redeliver-interval` |
| `jp.co.tis.lerna.payment.application.util.at-least-once-delivery.retry-timeout` | `lerna.util.akka.at-least-once-delivery.retry-timeout` |

### `sequence-factory` を使っている場合

設定ファイル (`*.conf`) の該当項目名を変更してください。  
※設定項目が大量にあるためすべてを記載しておりません。

| old | new |
| --- | --- |
| `jp.co.tis.lerna.payment.application.sequence-factory.*` | `lerna.util.sequence.*` |
