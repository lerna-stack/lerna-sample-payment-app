package jp.co.tis.lerna.payment.utility.scalatest

import lerna.util.lang.Equals
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

trait StandardSpec extends AnyWordSpecLike with SpecAssertions with Equals with Matchers
