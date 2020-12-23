package jp.co.tis.lerna.payment.presentation.util.api

import com.typesafe.config.ConfigFactory
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec

class ConfigCheck extends StandardSpec {
  "payment-app/presentation/src/main/resources/reference.conf" should {
    "各API の config が存在する" in {
      // ApiId (Enum)追加時に、このテストが落ちた場合は、 reference.conf に `API01 = ${jp.co.tis.lerna.payment.presentation.util.api.default.BASE}` のように新規API用 api config を追加する
      val config = ConfigFactory.defaultReference().getConfig("jp.co.tis.lerna.payment.presentation.util.api.default")

      ApiId.values.foreach { apiId =>
        expect {
          config.hasPath(apiId.toString)
        }
      }
    }
  }
}
