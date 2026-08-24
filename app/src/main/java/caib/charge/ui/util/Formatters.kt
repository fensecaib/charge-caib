package caib.charge.ui.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CurrencyFormatter = NumberFormat.getCurrencyInstance(Locale.CHINA).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

private val DateFormatter = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)
private val FullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

fun formatMoney(cents: Long): String = CurrencyFormatter.format(cents / 100.0)
fun formatSignedMoney(cents: Long, income: Boolean): String = (if (income) "+" else "-") + formatMoney(cents)
fun formatDay(date: LocalDate): String = date.format(DateFormatter)
fun formatFullDate(date: LocalDate): String = date.format(FullDateFormatter)
fun formatMonth(month: YearMonth): String = "${month.year}年${month.monthValue}月"
