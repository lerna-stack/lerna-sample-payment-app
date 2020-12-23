package jp.co.tis.lerna.payment.adapter.wallet

import jp.co.tis.lerna.payment.adapter.util.authorization.model.Subject
import jp.co.tis.lerna.payment.utility.scalatest.StandardSpec

final class CustomerIdSpec extends StandardSpec {

  "CustomerId.from" should {

    "create a CustomerID instance from the Subject" in {
      val rawString = "my-raw-subject"
      val subject   = Subject(rawString)
      val id        = CustomerId.from(subject)
      assert(id.value === rawString)
    }

  }

}
