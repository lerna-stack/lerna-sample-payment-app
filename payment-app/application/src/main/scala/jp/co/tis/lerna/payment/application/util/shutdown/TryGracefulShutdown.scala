package jp.co.tis.lerna.payment.application.util.shutdown

import akka.actor.Address

final case class TryGracefulShutdown(address: Address)
