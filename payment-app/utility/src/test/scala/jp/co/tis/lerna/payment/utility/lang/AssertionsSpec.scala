package jp.co.tis.lerna.payment.utility.lang

import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec

class AssertionsSpec extends StandardSpec {

  "Assertions - require" should {

    "条件を満たしている場合はエラーなし - inline" in {
      import jp.co.tis.lerna.payment.utility.lang.Assertions._

      require(1 === 1)
    }

    "条件を満たしている場合はエラーなし - block" in {
      import jp.co.tis.lerna.payment.utility.lang.Assertions._

      require {
        1 === 1
      }
    }

    "条件を満たさない場合は IllegalArgumentException - inline" in {
      import jp.co.tis.lerna.payment.utility.lang.Assertions._

      assertThrows[IllegalArgumentException] {
        require(1 === 2)
      }
    }

    "条件を満たさない場合は IllegalArgumentException - block" in {
      import jp.co.tis.lerna.payment.utility.lang.Assertions._

      assertThrows[IllegalArgumentException] {
        require {
          1 === 2
          "a" === "b"
        }
      }
    }
  }

  "Assertions - assert" should {

    "条件を満たしている場合はエラーなし - inline" in {
      Assertions.assert(1 === 1)
    }

    "条件を満たしている場合はエラーなし - block" in {
      Assertions.assert {
        1 === 1
      }
    }

    "条件を満たさない場合は AssertionError - inline" in {
      assertThrows[AssertionError] {
        Assertions.assert(1 === 2)
      }
    }

    "条件を満たさない場合は AssertionError - block" in {
      assertThrows[AssertionError] {
        Assertions.assert {
          1 === 2
          "a" === "b"
        }
      }
    }
  }

}
