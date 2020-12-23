package jp.co.tis.lerna.payment.application.ecpayment.issuing

import jp.co.tis.lerna.payment.adapter.ecpayment.issuing.model.{ HousePan, TerminalId }
import jp.co.tis.lerna.payment.adapter.wallet.WalletId

// 外部システム呼び出しためのRDBMS検索結果格納
final case class IssuingServicePayCredential(
    walletId: Option[WalletId],        // 会員.WalletID
    customerNumber: Option[String],    // 会員.お客様番号
    memberStoreId: String,             // ハウス加盟店.加盟店ID
    memberStoreNameEn: Option[String], // ハウス加盟店.加盟店名称（英語）
    memberStoreNameJp: Option[String], // ハウス加盟店.加盟店名称（日本語）
    contractNumber: String,            // 契約番号
    housePan: HousePan,                // カード番号
    terminalId: TerminalId,            // ハウス加盟店.端末識別番号
)
