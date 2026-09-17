package kr.baraplt.material.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val serverLinkedKey = booleanPreferencesKey("server_linked")

    val role: Flow<UserRole> = context.dataStore.data.map {
        runCatching { UserRole.valueOf(it[roleKey] ?: UserRole.STAFF.name) }
            .getOrDefault(UserRole.STAFF)
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
