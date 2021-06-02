# NOTE

[Schema Code Generation](https://scala-slick.org/doc/3.3.3/code-generation.html) と [MariaDB Connector/J](https://mariadb.com/kb/en/about-mariadb-connector-j/) を組み合わせて使用するためには幾つかの調整が必要です。
次のコードで MariaDB Connector/J を使用しても動作するよう調整しています。
[initdb](../../../../docker/mariadb/initdb) に定義されているスキーマに対して動作することは確認できていますが、
それ以外の場合には別途調整が必要になる可能性があることにご注意ください。

- [CustomJdbcModelBuilder](CustomMySQLModelBuilder.scala)
- [CustomJdbcModelComponentExt](./CustomJdbcModelComponentExt.scala)

*Slick 3.3.3* は *MariaDB* をサポートしていないため、  
*MariaDB* と *MySQL* に高い互換性があったとしても幾つかの調整が必要になる可能性があります。  
*Slick 3.3.3* でサポートされているデータベースは [Supported Databases](https://scala-slick.org/doc/3.3.3/supported-databases.html) から確認できます。
