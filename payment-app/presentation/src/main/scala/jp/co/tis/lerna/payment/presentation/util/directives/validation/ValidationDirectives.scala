package jp.co.tis.lerna.payment.presentation.util.directives.validation

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import com.wix.accord
import com.wix.accord.Validator
import jp.co.tis.lerna.payment.presentation.util.directives.GenLogContextDirective
import jp.co.tis.lerna.payment.presentation.util.errorhandling.ErrorMessage
import lerna.log.AppLogging

trait ValidationDirectives extends ValidationJsonSupport with GenLogContextDirective with AppLogging {
  import jp.co.tis.lerna.payment.presentation.util.errorhandling.AppExceptionHandler._

  def valid[T](target: T, validator: Validator[T]): Directive1[Unit] = {
    accord.validate(target)(validator) match {
      case com.wix.accord.Failure(violations) =>
        complete(
          StatusCodes.BadRequest -> ErrorMessage(
            message = violations.mkString,
            code = "CODE-003",
          ),
        )
      case _ =>
        // for 式で使いやすいように Directive1 にする
        provide(())
    }
  }

  def validEntity[T](
      umAndValidator: (FromRequestUnmarshaller[T], Validator[T]),
  ): Directive1[T] = {

    implicit val (um: FromRequestUnmarshaller[T], validator: Validator[T]) = umAndValidator

    extractLogContext flatMap { implicit logContext =>
      akka.http.scaladsl.server.Directives.entity(um) flatMap { model =>
        val result = accord.validate(model)

        result match {
          case com.wix.accord.Failure(violations) =>
            logger.debug(s"Request Validate Violation: ${violations.toString()}")
            complete(
              StatusCodes.BadRequest -> ErrorMessage(
                message = violations.mkString,
                code = "CODE-003",
              ),
            )
          case _ =>
            provide(model)
        }
      }

    }
  }

  def asValid[T](implicit
      um: FromRequestUnmarshaller[T],
      validator: Validator[T],
  ): (FromRequestUnmarshaller[T], Validator[T]) = (um, validator)

}
