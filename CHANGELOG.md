# 変更履歴

payment-app に関する注目すべき変更はこのファイルで文書化されます。

このファイルの書き方に関する推奨事項については、[Keep a Changelog](https://keepachangelog.com/ja/1.0.0/) を確認してください。

## Unreleased


TODO: sample の version 体系について検討

## Version 1.1.0
- `Changed` Read Model Updater を分散実行しスループットを向上
    - ※ tag の形式と offset の 保存テーブルが変更になったため、切替前後のeventの処理に注意（切替前のEventに未処理が存在する場合、切替後の処理対象とならない）

## Version 1.0.0

- Initial release
