package kr.baraplt.material.update

object UpdatePolicy {
    fun shouldCheck(debuggable: Boolean): Boolean = !debuggable

    enum class Path { FLEXIBLE, IMMEDIATE, STORE }

    fun path(flexibleAllowed: Boolean, immediateAllowed: Boolean): Path = when {
        flexibleAllowed -> Path.FLEXIBLE
        immediateAllowed -> Path.IMMEDIATE
        else -> Path.STORE
    }
}
