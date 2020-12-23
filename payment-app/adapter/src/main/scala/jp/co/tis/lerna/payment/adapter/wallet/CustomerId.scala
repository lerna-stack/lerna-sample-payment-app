package jp.co.tis.lerna.payment.adapter.wallet

import jp.co.tis.lerna.payment.adapter.util.authorization.model.Subject
import jp.co.tis.lerna.payment.adapter.util.authorization.model.Subject

final case class CustomerId(value: String) extends AnyVal

object CustomerId {
  def from(subject: Subject): CustomerId = CustomerId(subject.value)
}
