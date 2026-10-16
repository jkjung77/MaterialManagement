package kr.baraplt.material.domain

/** 하루 실적 한 줄. 단품이면 바코드가 있을 수 있다. */
data class DayLine(
    val codeNo: Int,
    val name: String,
    val qty: Int,
    val barcode: String = ""
)

data class DatedQty(val ownerId: Long, val workDate: String, val qty: Int)

data class TraceItem(val id: Long, val codeNo: Int, val name: String, val barcode: String = "")

object DailyTrace {
    fun dayOf(workDate: String): Int? = workDate.substringAfterLast('-').toIntOrNull()

    /** 수량이 있는 날. 달력에서 그 날을 표시할 때 쓴다. */
    fun activeDays(records: List<DatedQty>): Set<Int> =
        records.mapNotNull { if (it.qty > 0) dayOf(it.workDate) else null }.toSet()

    /** 고른 날에 생산한 항목. 수량이 0인 줄과 이름 없는 항목은 빼고, 번호 순으로 돌려준다. */
    fun onDay(day: Int, records: List<DatedQty>, items: List<TraceItem>): List<DayLine> {
        val byId = items.associateBy { it.id }
        return records
            .filter { it.qty > 0 && dayOf(it.workDate) == day }
            .groupBy { it.ownerId }
            .mapNotNull { (id, rows) ->
                val item = byId[id] ?: return@mapNotNull null
                DayLine(item.codeNo, item.name, rows.sumOf { it.qty }, item.barcode)
            }
            .sortedBy { it.codeNo }
    }
}
