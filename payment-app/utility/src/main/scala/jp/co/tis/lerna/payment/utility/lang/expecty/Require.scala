package jp.co.tis.lerna.payment.utility.lang.expecty

import com.eed3si9n.expecty._

private[lang] class RequireBase extends Recorder[Boolean, Unit] {
  val failEarly: Boolean    = true
  val showTypes: Boolean    = false
  val showLocation: Boolean = false

  class RequireListener extends RecorderListener[Boolean, Unit] {

    override def expressionRecorded(
        recordedExpr: RecordedExpression[Boolean],
        recordedMessage: () => String,
    ): Unit = {
      lazy val rendering: String = new ExpressionRenderer(showTypes).render(recordedExpr)
      if (!recordedExpr.value && failEarly) {
        val header = {
          val locStr = renderCodeLocationIfNeeded(recordedExpr.location)
          val msg    = renderRecordedMessage(recordedMessage())
          "requirement failed" + locStr + msg
        }
        throw new IllegalArgumentException(header + "\n\n" + rendering)
      }
    }

    override def recordingCompleted(
        recording: Recording[Boolean],
        recordedMessage: () => String,
    ): Unit = {
      if (!failEarly) {
        val failedExprs = recording.recordedExprs.filter(!_.value)
        if (failedExprs.nonEmpty) {
          val header = {
            val msg = renderRecordedMessage(recordedMessage())
            val locStr = {
              val locOption = failedExprs.headOption.map(_.location)
              locOption.map(renderCodeLocationIfNeeded).getOrElse("")
            }
            val requirementStr = if (failedExprs.size > 1) "requirements" else "requirement"
            requirementStr + " failed" + locStr + msg
          }
          val rendering = {
            val renderer = new ExpressionRenderer(showTypes)
            failedExprs.reverse.map(renderer.render(_)).mkString("\n")
          }
          throw new IllegalArgumentException(header + "\n\n" + rendering)
        }
      }
    }

    private def renderCodeLocationIfNeeded(location: Location): String = {
      if (showLocation) {
        " (" + location.relativePath + ":" + location.line.toString + ")"
      } else {
        ""
      }
    }

    private def renderRecordedMessage(message: String): String = {
      if (message.nonEmpty) {
        ": " + message
      } else {
        ""
      }
    }
  }

  val listener = new RequireListener
}

private[lang] class Require extends RequireBase with UnaryRecorder[Boolean, Unit]
