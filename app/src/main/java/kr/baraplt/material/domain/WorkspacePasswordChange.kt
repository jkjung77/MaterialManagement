package kr.baraplt.material.domain

object WorkspacePasswordChange {
    fun validate(current: String, next: String, confirm: String): String? {
        if (current.trim().isEmpty()) return "현재 공장 암호를 입력하세요"
        return WorkspaceSecret.validate(next, confirm)
            ?: if (current.trim() == next.trim()) "현재 암호와 같습니다" else null
    }
}
