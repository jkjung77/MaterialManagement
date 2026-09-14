package kr.baraplt.material.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val symbols = DecimalFormatSymbols(Locale.KOREA).apply {
    groupingSeparator = ','
    decimalSeparator = '.'
}

private val intFmt = DecimalFormat("#,##0", symbols)
private val qtyFmt = DecimalFormat("#,##0.###", symbols)
private val moneyFmt = DecimalFormat("#,##0", symbols)
private val pctFmt = DecimalFormat("0%", symbols)

fun formatQty(value: Double): String = qtyFmt.format(value)
fun formatInt(value: Int): String = intFmt.format(value)
fun formatMoney(value: Double): String = moneyFmt.format(kotlin.math.round(value))
fun formatWon(value: Double): String = formatMoney(value) + "원"
fun formatPct(ratio: Double): String = pctFmt.format(ratio)

fun parseNumber(text: String): Double? {
    val cleaned = text.replace(",", "").trim()
    if (cleaned.isEmpty()) return null
    return cleaned.toDoubleOrNull()
}

fun parseInt(text: String): Int? = parseNumber(text)?.toInt()
