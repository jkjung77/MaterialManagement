package kr.baraplt.material.domain

import java.security.MessageDigest

object WorkspaceSecret {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 32

    fun validate(password: String, confirm: String? = null): String? {
        val pw = password.trim()
        if (pw.length < MIN_LENGTH) return "공장 암호는 ${MIN_LENGTH}자 이상입니다"
        if (pw.length > MAX_LENGTH) return "공장 암호는 ${MAX_LENGTH}자까지입니다"
        if (confirm != null && pw != confirm.trim()) return "공장 암호가 일치하지 않습니다"
        return null
    }

    fun hash(workspaceId: String, password: String): String {
        val id = WorkspaceId.normalize(workspaceId)
        val raw = "material|$id|${password.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun matches(workspaceId: String, password: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return false
        return hash(workspaceId, password) == storedHash
    }
}
