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
private val qtyExactFmt = DecimalFormat("0.######", symbols)
private val moneyFmt = DecimalFormat("#,##0", symbols)
private val pctFmt = DecimalFormat("0%", symbols)

fun formatQty(value: Double): String = qtyFmt.format(value)

/**
 * 입력칸 재채움·BOM US 표시용. 화면용 formatQty(소수 3자리)로 다시 채우면 0.0001 같은 값이 0이 된다.
 * 천 단위 쉼표를 넣지 않는다: NumberField가 쉼표를 소수점으로 바꿔 "1,250"이 1.25가 된다.
 */
fun formatQtyExact(value: Double): String = qtyExactFmt.format(value)
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

/** 생산·실적 개수. 빈칸은 0(지우기), 소수·음수·너무 큰 수는 null. */
fun parseCount(text: String): Int? {
    if (text.isBlank()) return 0
    val value = parseNumber(text) ?: return null
    if (value < 0 || value > Int.MAX_VALUE || value != kotlin.math.floor(value)) return null
    return value.toInt()
}
