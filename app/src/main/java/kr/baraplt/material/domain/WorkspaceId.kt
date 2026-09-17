package kr.baraplt.material.domain

object WorkspaceId {
    const val MAX_LENGTH = 32

    fun normalize(raw: String): String = raw.trim()

    fun validate(raw: String): String? {
        val id = normalize(raw)
        if (id.isEmpty()) return "공장 ID를 입력하세요"
        if (id.length > MAX_LENGTH) return "공장 ID는 ${MAX_LENGTH}자까지입니다"
        return null
    }

    fun dbFileName(raw: String): String {
        val safe = normalize(raw).replace(Regex("""[\\/:*?"<>|]"""), "_")
        return "material_$safe.db"
    }
}
