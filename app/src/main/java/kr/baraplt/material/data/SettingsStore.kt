package kr.baraplt.material.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.baraplt.material.domain.UserRole
import kr.baraplt.material.domain.YearMonthKey

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val roleKey = stringPreferencesKey("role")
    private val pinKey = stringPreferencesKey("manager_pin")
    private val monthKey = stringPreferencesKey("working_month")
    private val seededKey = booleanPreferencesKey("seeded")
    private val staffNameKey = stringPreferencesKey("staff_name")

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
}
