package kr.baraplt.material.domain

enum class UserRole {
    MANAGER,
    STAFF
}

enum class MovementType(val label: String) {
    INBOUND("입고"),
    OUTBOUND("반출"),
    SCRAP("폐기"),
    ADJUST("재고조정")
}

enum class StockStatus {
    OK,
    LOW,
    CRITICAL
}

data class YearMonthKey(val year: Int, val month: Int) {
    val value: String get() = "%04d-%02d".format(year, month)

    fun previous(): YearMonthKey =
        if (month == 1) YearMonthKey(year - 1, 12) else YearMonthKey(year, month - 1)

    fun next(): YearMonthKey =
        if (month == 12) YearMonthKey(year + 1, 1) else YearMonthKey(year, month + 1)

    fun display(): String = "${year}년 ${month}월"

    fun dayCount(): Int = java.time.YearMonth.of(year, month).lengthOfMonth()

    companion object {
        fun parse(value: String): YearMonthKey {
            val parts = value.split("-")
            return YearMonthKey(parts[0].toInt(), parts[1].toInt())
        }

        fun current(): YearMonthKey {
            val now = java.time.LocalDate.now()
            return YearMonthKey(now.year, now.monthValue)
        }
    }
}

data class BomLine(
    val materialId: Long,
    val materialNo: Int,
    val materialName: String,
    val unit: String,
    val unitPrice: Double,
    val usQty: Double
) {
    val lineCost: Double get() = usQty * unitPrice
}

data class MaterialSnapshot(
    val id: Long,
    val codeNo: Int,
    val name: String,
    val unit: String,
    val packUnit: String,
    val unitPrice: Double,
    val safetyStock: Double,
    val leadTimeDays: Int,
    val opening: Double,
    val inbound: Double,
    val outbound: Double,
    val scrap: Double,
    val adjust: Double,
    val usage: Double,
    val purchaseAmount: Double,
    val scrapCost: Double
) {
    val current: Double
        get() = opening + inbound - outbound - scrap + adjust - usage

    val usageAmount: Double
        get() = usage * unitPrice

    val status: StockStatus
        get() = when {
            current <= 0.0 -> StockStatus.CRITICAL
            safetyStock > 0 && current <= safetyStock -> StockStatus.LOW
            else -> StockStatus.OK
        }

    val daysCover: Double?
        get() {
            if (usage <= 0.0) return null
            val ym = YearMonthKey.current()
            val days = ym.dayCount().coerceAtLeast(1)
            val daily = usage / days
            if (daily <= 0.0) return null
            return current / daily
        }
}

data class ProductSnapshot(
    val id: Long,
    val codeNo: Int,
    val name: String,
    val sellPrice: Double,
    val bom: List<BomLine>,
    val monthPlan: Int,
    val produced: Int
) {
    val materialCost: Double get() = bom.sumOf { it.lineCost }
    val materialRatio: Double get() = if (sellPrice <= 0) 0.0 else materialCost / sellPrice
    val usageAmount: Double get() = materialCost * produced
    val salesAmount: Double get() = sellPrice * produced
}

data class FinishedSnapshot(
    val id: Long,
    val codeNo: Int,
    val name: String,
    val sellPrice: Double,
    val productIds: List<Long>,
    val productNames: List<String>,
    val monthPlan: Int,
    val materialCost: Double
) {
    val materialRatio: Double get() = if (sellPrice <= 0) 0.0 else materialCost / sellPrice
}

data class MonthReport(
    val yearMonth: YearMonthKey,
    val salesAmount: Double,
    val usageAmount: Double,
    val purchaseAmount: Double,
    val scrapCost: Double,
    val producedQty: Int,
    val inboundQty: Double
) {
    val materialRatio: Double get() = if (salesAmount <= 0) 0.0 else usageAmount / salesAmount
    val grade: String
        get() = when {
            salesAmount <= 0 -> "-"
            materialRatio <= 0.55 -> "좋음"
            materialRatio <= 0.70 -> "보통"
            else -> "주의"
        }
}

data class RequiredMaterial(
    val materialId: Long,
    val codeNo: Int,
    val name: String,
    val unit: String,
    val current: Double,
    val required: Double,
    val shortage: Double,
    val leadTimeDays: Int
)
