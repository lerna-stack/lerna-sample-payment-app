# RDBMSアクセス処理実装ガイド

RDBMS にアクセスするコンポーネントを実装する方法を示します。

RDBMS へのアクセスは [Slick](http://slick.lightbend.com/) を使います。
Slick に関係するコンポーネントは全て DI で取得できるようになっています。

詳細は [JDBCServiceExample](/payment-app/example/src/main/scala/jp/co/tis/lerna/payment/example/readmodel/JDBCServiceExample.scala) を参照してください。

## テスト方法

RDBMS に依存したコンポーネントはテストコード毎にテスト用のデータを登録し、テストします。

具体的な方法は [JDBCSupportSpec](/payment-app/read-model-testkit/src/test/scala/jp/co/tis/lerna/payment/readmodel/JDBCSupportSpec.scala) を参照してください。
