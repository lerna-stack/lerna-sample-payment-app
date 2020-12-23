package jp.co.tis.lerna.payment.presentation.util.directives.validation

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.wix.accord.Violation
import spray.json.{ deserializationError, DefaultJsonProtocol, JsString, JsValue, RootJsonFormat }

trait ValidationJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object ViolationFormat extends RootJsonFormat[Violation] {
    override def write(violation: Violation): JsValue = JsString(violation.toString)
    override def read(json: JsValue): Violation = json match {
      case _ => deserializationError("Unsupported to deserialize Violation")
    }
  }
}
