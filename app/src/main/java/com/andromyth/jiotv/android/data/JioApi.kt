package com.andromyth.jiotv.android.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Native adaptation of the public webOS project's basic Jio auth/channel calls.
 * Jio endpoints are unofficial and can change. Does not bypass subscription or DRM.
 */
class JioApi(context: Context) {
    private val prefs = context.getSharedPreferences("jtv-session-v1", Context.MODE_PRIVATE)
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    var session: JioSession? = loadSession()
        private set

    private fun loadSession(): JioSession? = try {
        val raw = prefs.getString("session", null) ?: return null
        val j = JSONObject(raw)
        JioSession(j.getString("mobile"), j.getString("ssoToken"), j.getString("authToken"),
            j.optString("refreshToken"), j.optString("crmid"), j.optString("uniqueId"),
            j.getString("deviceId"), j.optString("userId"))
    } catch (_: Exception) { null }

    private fun saveSession(value: JioSession) {
        session = value
        prefs.edit().putString("session", JSONObject().apply {
            put("mobile", value.mobile); put("ssoToken", value.ssoToken); put("authToken", value.authToken)
            put("refreshToken", value.refreshToken); put("crmid", value.crmid); put("uniqueId", value.uniqueId)
            put("deviceId", value.deviceId); put("userId", value.userId)
        }.toString()).apply()
    }

    fun signOut() {
        session = null
        prefs.edit().remove("session").apply()
    }

    private fun req(url: String, method: String = "GET", body: String? = null, headers: Map<String, String> = emptyMap()): JSONObject {
        val parsed = URL(url)
        val host = parsed.host.lowercase()
        require(parsed.protocol == "https" && (host == "jio.com" || host.endsWith(".jio.com") || host.endsWith(".jiocdn.com"))) {
            "Blocked non-Jio endpoint"
        }
        val builder = Request.Builder().url(url).header("User-Agent", "okhttp/4.12.0")
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (method == "POST") builder.post((body ?: "{}").toRequestBody(jsonType)) else builder.get()
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Jio request failed (HTTP ${response.code}).")
            return try { if (text.isBlank()) JSONObject() else JSONObject(text) }
            catch (e: Exception) { throw IOException("Jio returned an unreadable response.", e) }
        }
    }

    private fun authHeaders() = mapOf("os" to "android", "devicetype" to "phone", "appname" to "RJIL_JioTV", "Content-Type" to "application/json")

    fun sendOtp(rawMobile: String) {
        val mobile = normalizeMobile(rawMobile)
        val number = Base64.encodeToString(("+91$mobile").toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        req("https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/send", "POST",
            JSONObject().put("number", number).toString(), authHeaders())
    }

    fun verifyOtp(rawMobile: String, otp: String): JioSession {
        val mobile = normalizeMobile(rawMobile)
        require(otp.trim().isNotEmpty()) { "Enter the OTP from Jio." }
        val deviceId = UUID.randomUUID().toString()
        val number = Base64.encodeToString(("+91$mobile").toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val deviceInfo = JSONObject().put("consumptionDeviceName", "unknown sdk_google_atv")
            .put("info", JSONObject().put("type", "android")
                .put("platform", JSONObject().put("name", "generic_atv"))
                .put("androidId", deviceId))
        val response = req("https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/verify", "POST",
            JSONObject().put("number", number).put("otp", otp.trim()).put("deviceInfo", deviceInfo).toString(), authHeaders())
        val user = response.optJSONObject("sessionAttributes")?.optJSONObject("user") ?: JSONObject()
        val sso = response.optString("ssoToken")
        val auth = response.optString("authToken")
        if (sso.isBlank() || auth.isBlank()) throw IOException(response.optString("message").ifBlank { "Jio did not return a usable sign-in session." })
        val value = JioSession(
            mobile = mobile, ssoToken = sso, authToken = auth, refreshToken = response.optString("refreshToken"),
            crmid = user.optString("subscriberId"), uniqueId = user.optString("unique"),
            deviceId = response.optString("deviceId").ifBlank { deviceId }, userId = user.optString("uid")
        )
        saveSession(value)
        return value
    }

    fun getChannels(): List<TvChannel> {
        val urls = listOf(
            "https://jiotvapi.cdn.jio.com/apis/v1.4/getMobileChannelList/get/?langId=6&devicetype=phone&os=android&usertype=JIO&version=422",
            "https://jiotvapi.cdn.jio.com/apis/v3.1/getMobileChannelList/get/?langId=6&os=android&devicetype=phone&usertype=JIO&version=422"
        )
        val json = urls.firstNotNullOfOrNull { url ->
            try { req(url) } catch (_: Exception) { null }
        } ?: throw IOException("Could not retrieve channels from Jio.")
        val items = json.optJSONArray("result") ?: throw IOException("Jio did not return a channel list.")
        val languages = mapOf("1" to "Hindi", "2" to "Marathi", "3" to "Punjabi", "4" to "Urdu",
            "5" to "Bengali", "6" to "English", "7" to "Malayalam", "8" to "Tamil",
            "9" to "Gujarati", "10" to "Odia", "11" to "Telugu", "12" to "Bhojpuri",
            "13" to "Kannada", "14" to "Assamese", "15" to "Nepali", "16" to "French")
        val channels = mutableListOf<TvChannel>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("channel_id")
            if (!id.matches(Regex("\\d{1,7}"))) continue
            val logo = item.optString("logoUrl")
            channels += TvChannel(
                id = id,
                name = item.optString("channel_name").ifBlank { "Channel $id" },
                logoUrl = if (logo.isNotBlank()) "https://jiotvimages.cdn.jio.com/dare_images/images/$logo" else "",
                group = item.optString("channelCategoryName").ifBlank { "Other" },
                language = languages[item.optString("channelLanguageId")] ?: "Other",
                isDrm = item.optBoolean("isDrm") || item.optString("streamType") == "mpd"
            )
        }
        if (channels.isEmpty()) throw IOException("Jio returned no usable channels.")
        return channels.distinctBy { it.id }.sortedBy { it.id.toLongOrNull() ?: Long.MAX_VALUE }
    }

    /** Full geturl/token-refresh/manifest proxy is a follow-up task; fail clearly for now. */
    fun resolvePlayback(channel: TvChannel): PlaybackInfo {
        if (channel.isDrm) throw IOException("Authorized Widevine playback is not yet implemented in this starter.")
        throw IOException("Stream resolution is not implemented in this starter yet.")
    }

    private fun normalizeMobile(value: String): String {
        var digits = value.filter { it.isDigit() }
        if (digits.length == 12 && digits.startsWith("91")) digits = digits.drop(2)
        require(digits.matches(Regex("\\d{10}"))) { "Enter a valid 10-digit Indian mobile number." }
        return digits
    }
}
