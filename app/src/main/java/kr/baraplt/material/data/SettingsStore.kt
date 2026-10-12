package kr.baraplt.material.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kr.baraplt.material.domain.ScreenLabels
import kr.baraplt.material.domain.UserRole
import kr.baraplt.material.domain.WorkspaceId
import kr.baraplt.material.domain.WorkspaceSecret
import kr.baraplt.material.domain.YearMonthKey
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val roleKey = stringPreferencesKey("role")
    private val pinKey = stringPreferencesKey("manager_pin")
    private val monthKey = stringPreferencesKey("working_month")
    private val seededKey = booleanPreferencesKey("seeded")
    private val staffNameKey = stringPreferencesKey("staff_name")
    private val workspaceIdKey = stringPreferencesKey("workspace_id")
    private val workspaceSecretsKey = stringPreferencesKey("workspace_secrets")
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")
    private val pendingQueueKey = stringPreferencesKey("sync_queue")
    private val lastSyncKey = longPreferencesKey("last_sync_at")
    private val mastersDirtyKey = longPreferencesKey("masters_dirty_at")
    private val serverLinkedKey = booleanPreferencesKey("server_linked")
    private val gradeGoodKey = intPreferencesKey("grade_good_percent")
    private val gradeNormalKey = intPreferencesKey("grade_normal_percent")
    private val screenLabelsKey = stringPreferencesKey("screen_labels")
    private val canEditMastersKey = booleanPreferencesKey("can_edit_masters")
    private val managerPasswordSetKey = booleanPreferencesKey("manager_password_set")
    private val mastersUntilKey = longPreferencesKey("masters_until")

    val screenLabels: Flow<ScreenLabels> = context.dataStore.data.map {
        ScreenLabels.decode(it[screenLabelsKey], it[workspaceIdKey].orEmpty())
    }

    suspend fun setScreenLabels(workspaceId: String, labels: ScreenLabels) {
        context.dataStore.edit { it[screenLabelsKey] = labels.cleaned().encode(workspaceId) }
    }

    val role: Flow<UserRole> = context.dataStore.data.map {
        runCatching { UserRole.valueOf(it[roleKey] ?: UserRole.STAFF.name) }
            .getOrDefault(UserRole.STAFF)
    }

    /** 서버가 정한 권한. 관리책임자가 아니어도 기초정보 수정 권한을 받을 수 있다. */
    val access: Flow<ServerAccess> = context.dataStore.data.map {
        val role = runCatching { UserRole.valueOf(it[roleKey] ?: UserRole.STAFF.name) }.getOrDefault(UserRole.STAFF)
        ServerAccess(
            role = role,
            canEditMasters = role == UserRole.MANAGER || (it[canEditMastersKey] ?: false),
            managerPasswordSet = it[managerPasswordSetKey] ?: true,
            mastersUntil = it[mastersUntilKey] ?: 0L
        )
    }

    suspend fun setMastersUntil(until: Long) {
        context.dataStore.edit { it[mastersUntilKey] = until }
    }

    suspend fun setAccess(role: UserRole, canEditMasters: Boolean, managerPasswordSet: Boolean? = null) {
        context.dataStore.edit {
            it[roleKey] = role.name
            it[canEditMastersKey] = canEditMasters
            if (managerPasswordSet != null) it[managerPasswordSetKey] = managerPasswordSet
        }
    }

    val managerPin: Flow<String> = context.dataStore.data.map { it[pinKey] ?: "0000" }

    val workingMonth: Flow<YearMonthKey> = context.dataStore.data.map {
        val raw = it[monthKey]
        if (raw.isNullOrBlank()) YearMonthKey.current() else YearMonthKey.parse(raw)
    }

    val seeded: Flow<Boolean> = context.dataStore.data.map { it[seededKey] ?: false }

    val staffName: Flow<String> = context.dataStore.data.map { it[staffNameKey] ?: "담당자" }

    val workspaceId: Flow<String> = context.dataStore.data.map { it[workspaceIdKey].orEmpty() }

    val workspaceSecrets: Flow<Map<String, String>> = context.dataStore.data.map {
        parseSecrets(it[workspaceSecretsKey])
    }

    val accessToken: Flow<String> = context.dataStore.data.map { it[accessTokenKey].orEmpty() }
    val refreshToken: Flow<String> = context.dataStore.data.map { it[refreshTokenKey].orEmpty() }
    val pendingQueue: Flow<String> = context.dataStore.data.map { it[pendingQueueKey] ?: "[]" }
    val lastSyncAt: Flow<Long> = context.dataStore.data.map { it[lastSyncKey] ?: 0L }
    val serverLinked: Flow<Boolean> = context.dataStore.data.map { it[serverLinkedKey] ?: false }

    val gradeBounds: Flow<Pair<Int, Int>> = context.dataStore.data.map {
        val good = it[gradeGoodKey] ?: 55
        val normal = it[gradeNormalKey] ?: 70
        good.coerceIn(1, 98) to normal.coerceIn(good + 1, 99)
    }

    suspend fun setGradeBounds(goodPercent: Int, normalPercent: Int) {
        val good = goodPercent.coerceIn(1, 98)
        val normal = normalPercent.coerceIn(good + 1, 99)
        context.dataStore.edit {
            it[gradeGoodKey] = good
            it[gradeNormalKey] = normal
        }
    }

    suspend fun setRole(role: UserRole) {
        context.dataStore.edit { it[roleKey] = role.name }
    }

    suspend fun setPin(pin: String) {
        context.dataStore.edit { it[pinKey] = pin }
    }

    suspend fun setWorkingMonth(month: YearMonthKey) {
        context.dataStore.edit { it[monthKey] = month.value }
    }

    suspend fun setSeeded(value: Boolean) {
        context.dataStore.edit { it[seededKey] = value }
    }

    suspend fun setStaffName(name: String) {
        context.dataStore.edit { it[staffNameKey] = name }
    }

    suspend fun setWorkspaceId(id: String) {
        context.dataStore.edit { it[workspaceIdKey] = id }
    }

    suspend fun setWorkspaceSecret(id: String, password: String) {
        val key = WorkspaceId.normalize(id)
        val hash = WorkspaceSecret.hash(key, password)
        context.dataStore.edit { prefs ->
            val map = parseSecrets(prefs[workspaceSecretsKey]).toMutableMap()
            map[key] = hash
            val obj = JSONObject()
            map.forEach { (k, v) -> obj.put(k, v) }
            prefs[workspaceSecretsKey] = obj.toString()
        }
    }

    suspend fun setTokens(access: String, refresh: String) {
        context.dataStore.edit {
            it[accessTokenKey] = access
            it[refreshTokenKey] = refresh
            it[serverLinkedKey] = access.isNotBlank()
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit {
            it.remove(accessTokenKey)
            it.remove(refreshTokenKey)
            it[serverLinkedKey] = false
        }
    }

    suspend fun setLastSync(at: Long) {
        context.dataStore.edit { it[lastSyncKey] = at }
    }

    suspend fun mastersDirtyAt(): Long =
        context.dataStore.data.map { it[mastersDirtyKey] ?: 0L }.first()

    suspend fun markMastersDirty() {
        context.dataStore.edit { it[mastersDirtyKey] = System.currentTimeMillis() }
    }

    suspend fun clearMastersDirty(stamp: Long) {
        context.dataStore.edit { if ((it[mastersDirtyKey] ?: 0L) == stamp) it.remove(mastersDirtyKey) }
    }

    suspend fun pendingItems(): org.json.JSONArray {
        val raw = context.dataStore.data.map { it[pendingQueueKey] ?: "[]" }.first()
        return runCatching { org.json.JSONArray(raw) }.getOrElse { org.json.JSONArray() }
    }

    suspend fun setPendingItems(arr: org.json.JSONArray) {
        context.dataStore.edit { it[pendingQueueKey] = arr.toString() }
    }

    suspend fun enqueue(item: JSONObject) {
        val arr = pendingItems()
        arr.put(item)
        setPendingItems(arr)
    }

    suspend fun verifyWorkspaceSecret(id: String, password: String): Boolean {
        val key = WorkspaceId.normalize(id)
        val stored = parseSecrets(
            context.dataStore.data.map { it[workspaceSecretsKey] }.first()
        )[key]
        return stored != null && WorkspaceSecret.matches(key, password, stored)
    }

    companion object {
        private fun parseSecrets(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                val obj = JSONObject(raw)
                obj.keys().asSequence().associateWith { obj.optString(it) }
            }.getOrDefault(emptyMap())
        }
    }
}

data class ServerAccess(
    val role: UserRole = UserRole.STAFF,
    val canEditMasters: Boolean = false,
    val managerPasswordSet: Boolean = true,
    val mastersUntil: Long = 0L
) {
    fun mastersOpen(now: Long = System.currentTimeMillis()): Boolean = canEditMasters || mastersUntil > now
}
