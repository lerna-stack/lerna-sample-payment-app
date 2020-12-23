# RPM パッケージビルドガイド

サーバー環境にインストールする RPM パッケージの作成方法を示します。

## RPM パッケージをビルド

下記のコマンドを実行するとリリース用の RPM パッケージを作成できます。

```bash
docker-compose run --rm sbt-rpmbuild clean rpm:packageBin
```

下記のディレクトリに RPM パッケージファイルが生成されます。

- `target/rpm/RPMS/noarch/`

### 臨時でバージョンをインクリメントする方法

サーバー上にインストールする際、既にインストールされたバージョンよりも大きいバージョンを持つ RPM パッケージでないと、インストールが拒否されます。

臨時でバージョンをインクリメントするには、下記のようなコマンドを実行します。

```
docker-compose run --rm sbt-rpmbuild -Drpm.release=<臨時バージョン> clean rpm:packageBin
```

`-Drpm.release=` に数字を指定すると臨時でバージョンをインクリメントできます。

アプリのバージョンが `0.1.0` で `rpm.release` が無指定だと、`0.1.0-1` というバージョンになります。
`-Drpm.release=3` のように指定すると `0.1.0-3` というバージョンになります。

### 同一環境でアプリケーションを複数起動するためのビルド方法

複数の目的のテストを同時に実施したい場合など、次のようにアプリケーションの名前を別にして RPM パッケージをビルドすることで同一環境にアプリケーションを複数デプロイできます。

* `lerna-sample-payment-app`
* `lerna-sample-payment-app2`

デフォルトでは、`lerna-sample-payment-app` としてアプリケーションが作成されます。

`lerna-sample-payment-app2` として作成する場合、以下のように実行コマンドにシステムプロパティを追加してください。

```bash
# 臨時バージョンを指定しない場合
docker-compose run --rm sbt-rpmbuild -Dproject.name=lerna-sample-payment-app2 clean rpm:packageBin

# 臨時バージョンを指定する場合
docker-compose run --rm sbt-rpmbuild -Dproject.name=lerna-sample-payment-app2 -Drpm.release=<臨時バージョン> clean rpm:packageBin
```

`target/rpm/RPMS/noarch/` ディレクトリ配下に、`lerna-sample-payment-app2-<アプリバージョン>-<臨時バージョン>.noarch.rpm` というファイルが作成されます。

このファイルを使用して、`lerna-sample-payment-app2` をデプロイしてください。
