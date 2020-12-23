package jp.co.tis.lerna.payment.adapter.util.authorization.model

import jp.co.tis.lerna.payment.adapter.wallet.ClientId

final case class AuthorizationResult(subject: Subject, clientId: ClientId)
