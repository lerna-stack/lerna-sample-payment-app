package jp.co.tis.lerna.payment.utility.lang.expecty

import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec

final class RequireSpec extends StandardSpec {
  "Require(failEarly=true)" should {
    val require = new Require()

    "throw no Exception when the requirement is fulfilled" in {
      require(1 == 1, "my custom message")
    }

    "throw IllegalArgumentException when the requirement is not fulfilled" in {
      val exception = intercept[IllegalArgumentException] {
        require(1 == 2, "my custom message")
      }
      exception.getMessage must include("requirement failed: my custom message")
      exception.getMessage must include("""require(1 == 2, "my custom message")""")
    }
  }

  "Require(failEarly=false)" should {
    val require = new Require {
      override val failEarly: Boolean = false
    }

    "throw no Exception when the requirement is fulfilled" in {
      require(1 == 1, "my custom message")
    }

    "throw IllegalArgumentException when the requirement is not fulfilled" in {
      val exception = intercept[IllegalArgumentException] {
        require(1 == 2, "my custom message")
      }
      exception.getMessage must include("requirement failed: my custom message")
      exception.getMessage must include("""require(1 == 2, "my custom message")""")
    }
  }

}
