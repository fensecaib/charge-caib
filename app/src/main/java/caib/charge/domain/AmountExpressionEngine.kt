package caib.charge.domain

import caib.charge.domain.model.MoneyLimits
import java.math.BigDecimal
import java.math.RoundingMode

enum class AmountError {
    EMPTY,
    INCOMPLETE,
    INVALID_NUMBER,
    TOO_MANY_DECIMALS,
    NON_POSITIVE,
    OVERFLOW,
}

data class AmountEvaluation(
    val expression: String,
    val resultCents: Long? = null,
    val error: AmountError? = null,
) {
    val isValid: Boolean get() = resultCents != null && error == null
}

/** Pure Kotlin calculator used by the built-in amount keypad. */
object AmountExpressionEngine {
    private val operators = setOf('+', '-')

    fun append(expression: String, key: Char): String {
        require(key.isDigit() || key == '.' || key in operators) { "不支持的按键: $key" }
        if (key in operators) {
            if (expression.isEmpty()) return expression
            return if (expression.last() in operators) expression.dropLast(1) + key else expression + key
        }

        val operand = expression.substringAfterLast('+').substringAfterLast('-')
        if (key == '.') {
            if ('.' in operand) return expression
            return expression + if (operand.isEmpty()) "0." else "."
        }
        val fractionLength = operand.substringAfter('.', missingDelimiterValue = "").length
        if ('.' in operand && fractionLength >= 2) return expression
        return expression + key
    }

    fun backspace(expression: String): String = expression.dropLast(1)

    fun evaluate(expression: String): AmountEvaluation {
        if (expression.isBlank()) return AmountEvaluation(expression, error = AmountError.EMPTY)
        if (expression.last() in operators || expression.last() == '.') {
            return AmountEvaluation(expression, error = AmountError.INCOMPLETE)
        }

        val operands = mutableListOf<String>()
        val ops = mutableListOf<Char>()
        var start = 0
        expression.forEachIndexed { index, char ->
            when {
                char in operators -> {
                    if (index == start) return AmountEvaluation(expression, error = AmountError.INVALID_NUMBER)
                    operands += expression.substring(start, index)
                    ops += char
                    start = index + 1
                }
                !char.isDigit() && char != '.' ->
                    return AmountEvaluation(expression, error = AmountError.INVALID_NUMBER)
            }
        }
        operands += expression.substring(start)

        val numbers = operands.map { value ->
            if (value.count { it == '.' } > 1) return AmountEvaluation(expression, error = AmountError.INVALID_NUMBER)
            if (value.substringAfter('.', "").length > 2) {
                return AmountEvaluation(expression, error = AmountError.TOO_MANY_DECIMALS)
            }
            value.toBigDecimalOrNull() ?: return AmountEvaluation(expression, error = AmountError.INVALID_NUMBER)
        }

        var result = numbers.first()
        ops.forEachIndexed { index, operator ->
            result = if (operator == '+') result + numbers[index + 1] else result - numbers[index + 1]
        }
        if (result <= BigDecimal.ZERO) return AmountEvaluation(expression, error = AmountError.NON_POSITIVE)
        val cents = try {
            result.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: ArithmeticException) {
            return AmountEvaluation(expression, error = AmountError.OVERFLOW)
        }
        if (cents !in MoneyLimits.MIN_CENTS..MoneyLimits.MAX_CENTS) {
            return AmountEvaluation(expression, error = AmountError.OVERFLOW)
        }
        return AmountEvaluation(expression, resultCents = cents)
    }
}
