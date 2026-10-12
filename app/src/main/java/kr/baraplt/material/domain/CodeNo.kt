package kr.baraplt.material.domain

/** 자재·단품·완제품 번호. 공장마다 1부터 이 값까지. */
object CodeNo {
    const val MAX = 1000

    fun valid(code: Int): Boolean = code in 1..MAX
}
