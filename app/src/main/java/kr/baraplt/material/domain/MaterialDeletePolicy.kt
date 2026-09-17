package kr.baraplt.material.domain

object MaterialDeletePolicy {
    fun blockReason(movementCount: Int, bomCount: Int): String? = when {
        movementCount > 0 -> "입출고 이력을 먼저 지우세요"
        bomCount > 0 -> "단품 투입자재에서 먼저 빼세요"
        else -> null
    }
}
