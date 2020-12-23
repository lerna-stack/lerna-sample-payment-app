package jp.co.tis.lerna.payment.entrypoint

import akka.actor.ActorSystem
import com.typesafe.config.Config
import jp.co.tis.lerna.payment.application.ApplicationDIDesign
import jp.co.tis.lerna.payment.gateway.GatewayDIDesign
import jp.co.tis.lerna.payment.presentation.PresentationDIDesign
import jp.co.tis.lerna.payment.readmodel.ReadModelDIDesign
import jp.co.tis.lerna.payment.utility.UtilityDIDesign
import wvlet.airframe._

object DIDesign extends DIDesign

@SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
trait DIDesign {

  val configDesign: Design = newDesign
    .bind[Config].toProvider((system: ActorSystem) => system.settings.config)

  @SuppressWarnings(Array("lerna.warts.CyclomaticComplexity"))
  def design(system: ActorSystem): Design =
    newDesign
      .bind[ActorSystem].toInstance(system)
      .bind[PaymentApp].toSingleton
      .add(configDesign)
      .add(PresentationDIDesign.presentationDesign)
      .add(GatewayDIDesign.gatewayDesign)
      .add(ApplicationDIDesign.applicationDesign)
      .add(ReadModelDIDesign.readModelDesign)
      .add(UtilityDIDesign.utilityDesign)
}
