package jp.co.tis.lerna.payment.presentation.ecpayment.issuing.payment.body

import com.wix.accord.Descriptions.Path
import com.wix.accord.dsl._
import com.wix.accord.transform.ValidationTransform
import com.wix.accord.{ Failure, RuleViolation, Success, Validator }
import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.AmountTran
import lerna.http.json.AnyValJsonFormat
import spray.json.{ DefaultJsonProtocol, JsonFormat, RootJsonFormat }

final case class IssuingServiceECPaymentRequest(amount: AmountTran)

object IssuingServiceECPaymentRequest {
  def isValid: Validator[Long] =
    (amount: Long) => {
      if (amount <= 0)
        Failure(
          Set(
            RuleViolation(
              amount,
              "is not a valid value",
              Path.empty,
            ),
          ),
        )
      else if (amount.toString.length > 10)
        Failure(
          Set(
            RuleViolation(
              amount,
              "is more than 10 digits",
              Path.empty,
            ),
          ),
        )
      else
        Success
    }

  @SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
  implicit val paymentRequestValidator: ValidationTransform.TransformedValidator[IssuingServiceECPaymentRequest] =
    validator[IssuingServiceECPaymentRequest] { model =>
      // amount が数値項目なので、データタイプチェック不要
      model.amount.value as "amount" is isValid
    }

  import DefaultJsonProtocol._

  implicit private val amountTranJsonFormat: JsonFormat[AmountTran] =
    AnyValJsonFormat(AmountTran.apply, AmountTran.unapply)

  implicit val jsonFormat: RootJsonFormat[IssuingServiceECPaymentRequest] =
    jsonFormat1(IssuingServiceECPaymentRequest.apply)

}
