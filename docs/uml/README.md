# UML

[PlantUML] で UML の図を生成します。

[PlantUML]: https://plantuml.com/ja/

## 準備

[Running Graphviz in Javascript](https://plantuml.com/ja/vizjs) から `visjs.jar` と実行プラットフォームに応じた `j2v8_*.jar` をダウンロードして [`lib/`](lib) ディレクトリに配置します。

[PlantUML ダウンロードページ](https://plantuml.com/ja/download) から `plantuml.jar` をダウンロードして [`lib/`](lib)  ディレクトリに配置します。

## 書き方

[src](./src) ディレクトリの直下に `.pluml` という拡張子で PlantUML のソースファイルを配置します。

[PlantUML] の公式ページを参考に、ソースを記述します。

後述する「図の生成」を行います。

## 図の生成

`plantuml.cmd` もしくは `plantuml.sh` をダブルクリックで実行し、GUI を起動します。

常駐するウィンドウが自動的にファイルの変更を検知し、[images](./images) ディレクトリ配下に図を生成します。
