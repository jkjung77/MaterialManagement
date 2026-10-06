package kr.baraplt.material.domain

object MonthCarry {
    fun <T> keepOtherMonths(
        server: List<T>,
        local: List<T>,
        monthOf: (T) -> String
    ): List<T> {
        val serverMonths = server.map(monthOf).toHashSet()
        return server + local.filter { monthOf(it) !in serverMonths }
    }
}
