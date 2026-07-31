package com.hausgroup.vpn

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Result of a network call: either a value or a human-readable error. */
sealed class ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>()
    data class Err(val message: String) : ApiResult<Nothing>()
}

data class Account(val email: String, val plan: String, val entitled: Boolean)

/**
 * Talks to the HausVPN control plane. All calls are blocking and must be run
 * off the main thread.
 */
class ApiClient(private val baseUrl: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()

    private fun base() = baseUrl.trimEnd('/')

    /** Register/login by email; returns a bearer token. */
    fun login(email: String): ApiResult<String> {
        return try {
            val body = JSONObject().put("email", email).toString().toRequestBody(json)
            val req = Request.Builder().url("${base()}/v1/auth/login").post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return ApiResult.Err(errorFrom(text, resp.code))
                ApiResult.Ok(JSONObject(text).getString("token"))
            }
        } catch (e: Exception) {
            ApiResult.Err(e.message ?: "Network error")
        }
    }

    fun me(token: String): ApiResult<Account> {
        return try {
            val req = Request.Builder().url("${base()}/v1/me")
                .header("Authorization", "Bearer $token").get().build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return ApiResult.Err(errorFrom(text, resp.code))
                val o = JSONObject(text)
                ApiResult.Ok(Account(o.optString("email"), o.optString("plan"), o.optBoolean("entitled")))
            }
        } catch (e: Exception) {
            ApiResult.Err(e.message ?: "Network error")
        }
    }

    /** Provision a device on the gateway and return a ready wg-quick config. */
    fun provisionDevice(token: String, name: String): ApiResult<String> {
        return try {
            val body = JSONObject().put("name", name).toString().toRequestBody(json)
            val req = Request.Builder().url("${base()}/v1/devices")
                .header("Authorization", "Bearer $token").post(body).build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.code == 402) return ApiResult.Err("Your subscription is inactive")
                if (!resp.isSuccessful) return ApiResult.Err(errorFrom(text, resp.code))
                ApiResult.Ok(JSONObject(text).getString("config"))
            }
        } catch (e: Exception) {
            ApiResult.Err(e.message ?: "Network error")
        }
    }

    private fun errorFrom(text: String, code: Int): String = try {
        JSONObject(text).optString("error").ifBlank { "Request failed ($code)" }
    } catch (_: Exception) {
        "Request failed ($code)"
    }
}
