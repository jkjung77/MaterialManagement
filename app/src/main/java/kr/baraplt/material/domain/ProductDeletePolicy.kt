package kr.baraplt.material.domain

object ProductDeletePolicy {
    fun blockReason(productionCount: Int, finishedCount: Int): String? = when {
        productionCount > 0 -> "생산실적을 먼저 지우세요"
        finishedCount > 0 -> "완제품 구성에서 먼저 빼세요"
        else -> null
    }
}
