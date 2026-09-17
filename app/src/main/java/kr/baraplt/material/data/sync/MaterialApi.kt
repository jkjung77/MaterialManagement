package kr.baraplt.material.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MaterialApi(private val baseUrl: String = DEFAULT_BASE) {

    suspend fun createWorkspace(code: String, password: String, managerName: String): ApiResult<LoginData> =
        request("POST", "/workspaces", auth = null, body = JSONObject()
            .put("code", code)
            .put("password", password)
            .put("name", code)
            .put("managerName", managerName)
        ) { LoginData.from(it.optJSONObject("data") ?: it) }

    suspend fun login(workspaceId: String, password: String, staffName: String): ApiResult<LoginData> =
        request("POST", "/auth/login", auth = null, body = JSONObject()
            .put("workspaceId", workspaceId)
            .put("password", password)
            .put("staffName", staffName)
        ) { LoginData.from(it.optJSONObject("data") ?: it) }

    suspend fun changeWorkspacePassword(token: String, current: String, next: String): ApiResult<JSONObject> =
        request(
            "PUT",
            "/workspaces/me/password",
            auth = token,
            body = JSONObject().put("currentPassword", current).put("password", next)
        ) { it.optJSONObject("data") ?: it }

    suspend fun refresh(refreshToken: String): ApiResult<LoginData> =
        request("POST", "/auth/refresh", auth = null, body = JSONObject().put("refreshToken", refreshToken)) {
            LoginData.from(it.optJSONObject("data") ?: it)
        }

    suspend fun snapshot(token: String, yearMonth: String): ApiResult<JSONObject> =
        request("GET", "/sync/snapshot?yearMonth=$yearMonth", auth = token) { root ->
            root.optJSONObject("data") ?: JSONObject()
        }

    suspend fun push(token: String, body: JSONObject): ApiResult<JSONObject> =
        request("POST", "/sync/push", auth = token, body = body) { it.optJSONObject("data") ?: it }

    suspend fun deleteMovement(token: String, item: JSONObject): ApiResult<JSONObject> =
        request("POST", "/sync/delete-movement", auth = token, body = item) { it.optJSONObject("data") ?: it }

    suspend fun deleteMaterial(token: String, item: JSONObject): ApiResult<JSONObject> =
        request("POST", "/sync/delete-material", auth = token, body = item) { it.optJSONObject("data") ?: it }

    suspend fun deleteProduct(token: String, item: JSONObject): ApiResult<JSONObject> =
        request("POST", "/sync/delete-product", auth = token, body = item) { it.optJSONObject("data") ?: it }

    suspend fun deleteFinished(token: String, item: JSONObject): ApiResult<JSONObject> =
        request("POST", "/sync/delete-finished", auth = token, body = item) { it.optJSONObject("data") ?: it }

    suspend fun importBundle(token: String, bundle: JSONObject): ApiResult<JSONObject> =
        request("POST", "/sync/import", auth = token, body = bundle) { it.optJSONObject("data") ?: it }

    suspend fun stocktake(token: String, body: JSONObject): ApiResult<JSONObject> =
        request("POST", "/stocktake", auth = token, body = body) { it.optJSONObject("data") ?: it }

    suspend fun closeMonth(token: String, yearMonth: String): ApiResult<JSONObject> =
        request("POST", "/months/$yearMonth/close", auth = token) { it.optJSONObject("data") ?: it }

    suspend fun reopenMonth(token: String, yearMonth: String): ApiResult<JSONObject> =
        request("DELETE", "/months/$yearMonth/close", auth = token) { it.optJSONObject("data") ?: it }

    private suspend fun <T> request(
        method: String,
        path: String,
        auth: String?,
        body: JSONObject? = null,
        parse: (JSONObject) -> T
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = if (method == "DELETE" && body == null) "DELETE" else method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            if (auth != null) setRequestProperty("Authorization", "Bearer $auth")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.readText().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (conn.responseCode in 200..299 && json.optBoolean("ok", true)) {
                ApiResult.Ok(parse(json))
            } else {
                ApiResult.Err(
                    json.optString("code").ifBlank { "HTTP_${conn.responseCode}" },
                    json.optString("message").ifBlank { "서버 오류 ${conn.responseCode}" },
                    conn.responseCode
                )
            }
        } catch (e: IOException) {
            ApiResult.Err("NETWORK", e.message ?: "서버에 연결할 수 없습니다", 0)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_BASE = "https://material.jayoo.kr/api/v1"
    }
}

sealed class ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>()
    data class Err(val code: String, val message: String, val http: Int) : ApiResult<Nothing>()
}

data class LoginData(
    val accessToken: String,
    val refreshToken: String,
    val workspaceCode: String,
    val userName: String,
    val role: String
) {
    companion object {
        fun from(obj: JSONObject): LoginData {
            val ws = obj.optJSONObject("workspace") ?: JSONObject()
            val user = obj.optJSONObject("user") ?: JSONObject()
            return LoginData(
                accessToken = obj.optString("accessToken"),
                refreshToken = obj.optString("refreshToken"),
                workspaceCode = ws.optString("code"),
                userName = user.optString("name"),
                role = user.optString("role").ifBlank { "STAFF" }
            )
        }
    }
}

fun JSONArray.toObjList(): List<JSONObject> = buildList {
    for (i in 0 until length()) add(getJSONObject(i))
}
