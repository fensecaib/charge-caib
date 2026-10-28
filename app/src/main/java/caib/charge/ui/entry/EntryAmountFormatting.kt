package caib.charge.ui.entry

import caib.charge.domain.AmountError
import caib.charge.ui.util.formatMoney
import java.math.BigDecimal

internal fun centsToExpression(cents: Long): String =
    BigDecimal(cents).movePointLeft(2).stripTrailingZeros().toPlainString()

internal fun amountResultText(resultCents: Long?, error: AmountError?): String = when {
    resultCents != null && error == null -> formatMoney(resultCents)
    error != null && error != AmountError.EMPTY -> "待计算"
    else -> "¥0.00"
}

internal fun amountErrorMessage(error: AmountError?): String = when (error) {
    null -> "金额有效，可完成保存"
    AmountError.EMPTY -> "请输入金额"
    AmountError.INCOMPLETE -> "请补全金额表达式"
    AmountError.INVALID_NUMBER -> "金额格式不正确"
    AmountError.TOO_MANY_DECIMALS -> "金额最多保留两位小数"
    AmountError.NON_POSITIVE -> "金额结果必须大于零"
    AmountError.OVERFLOW -> "金额超出允许范围"
}
