# 変更履歴

payment-app に関する注目すべき変更はこのファイルで文書化されます。

このファイルの書き方に関する推奨事項については、[Keep a Changelog](https://keepachangelog.com/ja/1.0.0/) を確認してください。
[Calendar Versioning — CalVer](https://calver.org/) `YYYY.MM.MICRO` を採用しています。

## [Unreleased]
[Unreleased]: https://github.com/lerna-stack/lerna-sample-payment-app/compare/v2023.12.0...main

## [v2023.12.0] - 2023-12-14
[v2023.12.0]: https://github.com/lerna-stack/lerna-sample-payment-app/compare/v2022.3.0...v2023.12.0

### ADDED
- 実装ガイド「ClusterShardingのEntity実装時の注意点」を追加しました [PR#45](https://github.com/lerna-stack/lerna-sample-payment-app/pull/45)

### CHANGED
- 「プログラミングスタイルガイド」に Blocking/IO と設定値に関する注意点を記載しました [PR#45](https://github.com/lerna-stack/lerna-sample-payment-app/pull/45)

## [v2022.3.0] - 2022-3-25
[v2022.3.0]: https://github.com/lerna-stack/lerna-sample-payment-app/compare/v2021.10.0...v2022.3.0

### CHANGED
- lerna-app-library 3.0.0 から 3.0.1 に更新しました


## [v2021.10.0] - 2021-10-22
[v2021.10.0]: https://github.com/lerna-stack/lerna-sample-payment-app/compare/v2021.7.0...v2021.10.0

### CHANGED

- lerna-app-library 2.0.0 から 3.0.0 に更新しました
- wiremock-jre8 2.27.2 から 2.30.1 に更新しました  
  バイナリ互換性を維持しやすくするため、lerna-app-library が使用する wiremock-jre8 と同じバージョンとしています。


## [v2021.7.0] - 2021-7-16
[v2021.7.0]: https://github.com/lerna-stack/lerna-sample-payment-app/compare/v1.1.0...v2021.7.0

### ADDED
- [README](README.md) に Management APIs の使用方法を記載しました

### CHANGED
- `lerna-app-library-2.0.0` に更新しました
    - `lerna-management` の更新に伴い、
      次の2つの HTTP APIs は Long 値から Double 値を返すように変更します。
        - `/metrics/rmu/sales_detail/ec_house_money/number_of_singleton`
        - `/metrics/system-metrics/jvm-memory/heap/max`
- `Scala 2.12.13` に更新しました
- `sbt-wartremover 2.4.13` に更新しました
- `sbt-scoverage 1.8.2` に更新しました
- Akka typed 対応のため、 `PaymentActor` から `self` にメッセージを送る際の処理を変更しました
    - graceful shutdown 時のレイテンシが増加する可能性があります
- Akka typed 対応のため、 `PaymentActor` からのレスポンスメッセージを変更しました
    -  `Status.Failure(exception)` -> 専用クラス化
    - ※ Response, Event の互換性が崩れる

## [v1.1.0] - 2021-3-30
[v1.1.0]: https://github.com/lerna-stack/lerna-sample-payment-app/compare/v1.0.0...v1.1.0
- `Changed` Read Model Updater を分散実行しスループットを向上
    - ※ tag の形式と offset の 保存テーブルが変更になったため、切替前後のeventの処理に注意（切替前のEventに未処理が存在する場合、切替後の処理対象とならない）

## [v1.0.0] - 2020-12-23
[v1.0.0]: https://github.com/lerna-stack/lerna-sample-payment-app/releases/tag/v1.0.0

- Initial release
