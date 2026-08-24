package caib.charge.data.transfer

import caib.charge.domain.TextNormalizer
import caib.charge.domain.model.EntryType
import caib.charge.domain.model.MoneyLimits
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

internal object CsvValueParser {
    fun headerKey(value: String): String = TextNormalizer.normalize(value)
        .replace(headerSeparators, "")

    fun parseType(value: String): EntryType = when (headerKey(value)) {
        "支出", "出", "expense", "expenses", "out", "payment" -> EntryType.EXPENSE
        "收入", "入", "income", "in", "earning", "earnings" -> EntryType.INCOME
        else -> throw CsvFormatException("无法识别收支类型“$value”")
    }

    fun parseAmount(value: String): Long {
        val withCurrencyRemoved = value.trim().replace(currencyPrefix, "")
        if (!plainAmount.matches(withCurrencyRemoved) && !groupedAmount.matches(withCurrencyRemoved)) {
            throw CsvFormatException("金额格式无效")
        }
        val cleaned = withCurrencyRemoved.replace(",", "")
        val amount = cleaned.toBigDecimalOrNull() ?: throw CsvFormatException("金额格式无效")
        if (amount.scale().coerceAtLeast(0) > 2) throw CsvFormatException("金额最多保留两位小数")
        val cents = try {
            amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: ArithmeticException) {
            throw CsvFormatException("金额超出范围")
        }
        if (cents !in MoneyLimits.MIN_CENTS..MoneyLimits.MAX_CENTS) {
            throw CsvFormatException("金额必须在 0.01 到 999,999,999.99 元之间")
        }
        return cents
    }

    fun parseDate(value: String): LocalDate {
        val trimmed = value.trim()
        dateFormats.forEach { formatter ->
            try {
                return LocalDate.parse(trimmed, formatter)
            } catch (_: DateTimeParseException) {
                // Try the next supported format.
            }
        }
        dateTimeFormats.forEach { formatter ->
            try {
                return LocalDateTime.parse(trimmed, formatter).toLocalDate()
            } catch (_: DateTimeParseException) {
                // Try the next supported format.
            }
        }
        throw CsvFormatException("日期格式无效“$value”")
    }

    fun protectSpreadsheet(value: String): String {
        val first = value.trimStart().firstOrNull()
        return if (value.startsWith('\'') || (first != null && first in formulaPrefixes)) "'$value" else value
    }

    fun unprotectSpreadsheet(value: String): String {
        if (value.startsWith("''")) return value.drop(1)
        val protectedFirst = value.drop(1).trimStart().firstOrNull()
        return if (value.startsWith('\'') && protectedFirst != null && protectedFirst in formulaPrefixes) {
            value.drop(1)
        } else {
            value
        }
    }

    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        strictFormatter("uuuu-M-d"),
        strictFormatter("uuuu/M/d"),
        strictFormatter("uuuu.M.d"),
        DateTimeFormatter.BASIC_ISO_DATE,
        strictFormatter("uuuu年M月d日"),
    )
    private val dateTimeFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        strictFormatter("uuuu-M-d H:mm"),
        strictFormatter("uuuu-M-d H:mm:ss"),
        strictFormatter("uuuu/M/d H:mm"),
        strictFormatter("uuuu/M/d H:mm:ss"),
        strictFormatter("uuuu.M.d H:mm"),
        strictFormatter("uuuu.M.d H:mm:ss"),
        strictFormatter("uuuu年M月d日 H:mm"),
        strictFormatter("uuuu年M月d日 H:mm:ss"),
        strictFormatter("uuuu-M-d'T'H:mm"),
        strictFormatter("uuuu-M-d'T'H:mm:ss"),
        strictFormatter("uuuu/M/d'T'H:mm"),
        strictFormatter("uuuu/M/d'T'H:mm:ss"),
        strictFormatter("uuuu.M.d'T'H:mm"),
        strictFormatter("uuuu.M.d'T'H:mm:ss"),
    )

    private fun strictFormatter(pattern: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)

    private val plainAmount = Regex("\\d+(?:\\.\\d{1,2})?")
    private val groupedAmount = Regex("\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?")
    private val headerSeparators = Regex("[\\s_\\-/]+")
    private val currencyPrefix = Regex("(?i)^(?:[￥¥]|RMB|CNY)\\s*")
    private val formulaPrefixes = setOf('=', '+', '-', '@')
}
