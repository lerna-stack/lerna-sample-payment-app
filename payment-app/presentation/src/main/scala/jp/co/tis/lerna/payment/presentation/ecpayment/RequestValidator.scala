package jp.co.tis.lerna.payment.presentation.ecpayment

import com.wix.accord.Validator
import com.wix.accord.dsl.{ validator, _ }
import lerna.validation.CustomCombinators._

object RequestValidator {
  @SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
  implicit val walletShopIdValidator: Validator[String] = validator { walletShopId =>
    walletShopId.length should be <= 40 as "walletShopId length" // 桁数チェック
    walletShopId is `半角` as "walletShopId"                       // 半角文字
  }
  @SuppressWarnings(Array("org.wartremover.contrib.warts.MissingOverride"))
  implicit val orderIdValidator: Validator[String] = validator { orderId =>
    orderId.length should be <= 27 as "orderId length" // 桁数チェック
    orderId is `半角英数字` as "orderId"                    // 半角英数字チェック
  }
}
