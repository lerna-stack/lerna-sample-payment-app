# RDBMSテーブル定義更新ガイド

リレーショナルデータベースのテーブル定義を更新した際のプロジェクト内の関連ファイルを更新する手段を記述する。

## 1. `docker/mariadb/initdb` に 初期化に使う SQL を追加

SQL ファイルは、ファイル名のアルファベット昇順で実行されるため、
先頭に prefix として番号を付与する。

詳細は [MariaDB Initializing Fresh Instance](https://hub.docker.com/_/mariadb#:~:text=Initializing%20a%20fresh%20instance) を参照すること。

## 2. ローカル環境の MariaDB に反映

データベースを破棄して、再構築する必要があります。  

```bash
docker-compose down --volumes
docker-compose up
```

## 3. Slick のテーブル定義を生成

```bash
sbt slick-codegen/run scalafmt scalafmt
```
※ `scalafmt` 1 回だとなぜかフォーマットされないので注意

## 4. テスト用の TableSeeds を更新

[TableSeeds.scala](../payment-app/read-model-testkit/src/main/scala/jp/co/tis/lerna/payment/readmodel/TableSeeds.scala) を手作業で更新

- 追加されたテーブルの RowSeed を追加
    - 論理削除フラグには `logicalDeleteFlagAsNotDeleted` を設定
    - それ以外の項目には `arbitraryValue` を設定
- 削除されたテーブルの RowSeed を削除
- カラムが増減したテーブルの RowSeed の列数を調整

## 5. 変更をコミット

テーブルの変更点がわかりやすいように DDL のみ独立してコミットする

```bash
git add docker/mariadb/initdb
git commit
git add payment-app/read-model/
git commit
```
