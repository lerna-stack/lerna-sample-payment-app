package jp.co.tis.lerna.payment.utility.lang

import com.eed3si9n.expecty._
import jp.co.tis.lerna.payment.utility.lang.expecty.Require

object Assertions extends Assertions

private[utility] trait Assertions {

  /** 引数が事前条件を満たしているかチェックします。
    * 事前条件に違反している場合は [[scala.IllegalArgumentException]] をスローします。
    */
  val require = new Require {
    override val failEarly: Boolean = false
  }

  /** 事前条件・事後条件が満たされているかチェックします。
    * 条件に違反している場合は [[java.lang.AssertionError]] をスローします。
    */
  val assert = new Expecty {
    override val failEarly: Boolean = false
  }
}
