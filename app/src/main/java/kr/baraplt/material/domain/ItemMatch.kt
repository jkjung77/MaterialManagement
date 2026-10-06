package kr.baraplt.material.domain

object ItemMatch {
    fun matches(codeNo: Int, name: String, raw: String, barcode: String = ""): Boolean {
        val query = raw.trim()
        if (query.isEmpty()) return true
        if (barcode.equals(query, true)) return true
        val tokens = query.split(Regex("[\\s.]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("NO", true) }
        if (tokens.isEmpty()) return name.contains(query, true)
        return tokens.all { token ->
            val number = token.toIntOrNull()
            if (number != null) codeNo == number else name.contains(token, true) || barcode.contains(token, true)
        }
    }
}

fun ratioGrade(salesAmount: Double, ratio: Double, goodPercent: Int = 55, normalPercent: Int = 70): String {
    if (salesAmount <= 0.0) return "-"
    val good = goodPercent.coerceIn(1, 98) / 100.0
    val normal = normalPercent.coerceIn(goodPercent + 1, 99) / 100.0
    return when {
        ratio <= good -> "좋음"
        ratio <= normal -> "보통"
        else -> "주의"
    }
}
