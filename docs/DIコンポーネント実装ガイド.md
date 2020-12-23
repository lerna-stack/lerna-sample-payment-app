# DIコンポーネント実装ガイド

DIコンポーネントを登録したり、利用したりする方法を示します。

## コンポーネントの登録

各サブプロジェクトごとにある `*DIDesign.scala` ファイルにコンポーネントを登録します。

登録方法は既に登録済みのコンポーネントや [Airframe 公式ドキュメント](https://wvlet.org/airframe/docs/airframe.html#design) の Design の章を参考にしてください。

- Presentation
    - [PresentationDIDesign.scala](/payment-app/presentation/src/main/scala/jp/co/tis/lerna/payment/presentation/PresentationDIDesign.scala)
- Gateway
    - [GatewayDIDesign.scala](/payment-app/gateway/src/main/scala/jp/co/tis/lerna/payment/gateway/GatewayDIDesign.scala)
- Application
    - [ApplicationDIDesign.scala](/payment-app/application/src/main/scala/jp/co/tis/lerna/payment/application/ApplicationDIDesign.scala)
- ReadModel
    - [ReadModelDIDesign.scala](/payment-app/read-model/src/main/scala/jp/co/tis/lerna/payment/readmodel/ReadModelDIDesign.scala)
- Utility
    - [UtilityDIDesign.scala](/payment-app/utility/src/main/scala/jp/co/tis/lerna/payment/utility/UtilityDIDesign.scala)

## プロダクションコードでのコンポーネントの利用

コンポーネントを利用する部品は `class` で作成してください。
（`trait` や `object` はコンストラクタを定義できないため）

必要なコンポーネントの **トレイト** をコンストラクタの引数として指定します。

```scala
//      ExampleComponent トレイトを指定する ↓
class ExampleLogic(exampleComponent: ExampleComponent) {
  // ...
}
```

`*DIDesign` では **トレイト** への **実装クラス**（もしくはオブジェクト）のマッピングが定義されています。

実際に上記の例の `ExampleLogic` クラスがインスタンス化される際は、
`ExampleComponent` トレイトにマッピングされた実装クラスのインスタンスが
コンストラクタ経由で自動的に渡されます。

## テストでのコンポーネントの利用

`lerna.testkit.airframe.DISessionSupport` を使うと簡単に利用できます。

詳しくは [DISessionSupportSpec](https://github.com/lerna-stack/lerna-app-library/tree/main/lerna-testkit/src/test/scala/lerna/testkit/airframe/DISessionSupportSpec.scala) を参照してください。
