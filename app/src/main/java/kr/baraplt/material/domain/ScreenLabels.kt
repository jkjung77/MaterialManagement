package kr.baraplt.material.domain

data class ScreenLabels(
    val title: String = "",
    val materials: String = "",
    val production: String = "",
    val report: String = ""
) {
    fun cleaned(): ScreenLabels = ScreenLabels(clean(title), clean(materials), clean(production), clean(report))

    fun encode(workspaceId: String): String =
        listOf(workspaceId, title, materials, production, report).joinToString("\n") { clean(it, 40) }

    companion object {
        const val MAX = 10

        fun decode(raw: String?, workspaceId: String): ScreenLabels {
            val parts = raw.orEmpty().split("\n")
            if (parts.size < 5 || parts[0] != workspaceId || workspaceId.isBlank()) return ScreenLabels()
            return ScreenLabels(parts[1], parts[2], parts[3], parts[4]).cleaned()
        }

        fun withSub(name: String, sub: String): String = if (sub.isBlank()) name else "$name($sub)"

        private fun clean(text: String, max: Int = MAX): String =
            text.replace(Regex("[\\r\\n\\t]"), " ").trim()
                .removePrefix("(").removeSuffix(")").trim()
                .take(max)
    }
}
