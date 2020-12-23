# サブプロジェクト特有の実装方針や注意点

各サブプロジェクトの役割など詳細については [プロジェクト構成詳解](../プロジェクト構成詳解.md) を参照してください。

## Adapter

### サンプルコード

[`<project root>/payment-app/adapter/`](/payment-app/adapter) ディレクトリ配下のコードを参照してください。 

## Application

### サンプルコード

[`<project root>/payment-app/application/`](/payment-app/application) ディレクトリ配下のコードを参照してください。 

### ReadModelUpdaterのタグ付け方法

[ReadModelUpdater用タグ付け](application/ReadModelUpdater用タグ付け.md) を参照してください。

### マルチテナント化されたClusterSharding Actorの実装方法
[マルチテナント化されたClusterShardingの実装ガイド](application/マルチテナント化されたClusterShardingの実装ガイド.md) を参照してください。

## EntryPoint

### サンプルコード

[`<project root>/payment-app/entrypoint/`](/payment-app/entrypoint) ディレクトリ配下のコードを参照してください。 

## Gateway

### サンプルコード

[`<project root>/payment-app/gateway/`](/payment-app/gateway) ディレクトリ配下のコードを参照してください。 

## Presentation

### サンプルコード

[`<project root>/payment-app/presentation/`](/payment-app/presentation) ディレクトリ配下のコードを参照してください。 

### ファイルの単位

原則、1つのエンドポイント（URL）ごとに1つのファイルを作成します。

ファイルには以下が含まれます。

- Akka HTTP の Route 定義
- Request/Response の JSON をマッピングする case class
- Request/Response の JSON をマッピングするための JsonFormat
- Request の case class の Validator

Route 定義は class の中に定義し、JsonFormat と Validator は Route 定義を行った class のコンパニオンオブジェクトに定義します。

## ReadModel

### サンプルコード

[`<project root>/payment-app/read-model/`](/payment-app/read-model) ディレクトリ配下のコードを参照してください。 

## Utility

### サンプルコード

[`<project root>/payment-app/utility/`](/payment-app/utility) ディレクトリ配下のコードを参照してください。 

## Example

### サンプルコード

[`<project root>/payment-app/example/`](/payment-app/example) ディレクトリ配下のコードを参照してください。 
