package org.sevcator.miniclash

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class Preferences(
    var port: String = "",
    var username: String = "",
    var password: String = "",
    var ipv6: Boolean = false,
    var logLevel: String = "silent",
    var userAgent: String = "",
    var hwid: String = "",
    var sendHwid: Boolean = false,
    var tun: Boolean = true,
    var overwriteKey: String = "",
    var overwriteValue: String = "",
)

data class Subscription(
    val id: String,
    var url: String,
    var title: String,
    var description: String = "",
    var userInfo: String = "",
    var supportUrl: String = "",
    var content: String = "",
    var lastUpdated: Long = 0,
)

class Store(private val context: Context) {
    private val file = context.getSharedPreferences("mini_clash", Context.MODE_PRIVATE)
    val settings: Preferences = JSONObject(file.getString("settings", "{}") ?: "{}").let {
        Preferences(it.optString("port"), it.optString("username"), it.optString("password"),
            it.optBoolean("ipv6"), it.optString("logLevel", "silent"), it.optString("userAgent"),
            it.optString("hwid"), it.optBoolean("sendHwid"), it.optBoolean("tun", true),
            it.optString("overwriteKey"), it.optString("overwriteValue"))
    }
    val subscriptions = mutableListOf<Subscription>()
    val chain = mutableListOf<String>()
    var activeId: String = file.getString("activeId", "") ?: ""
    val actualPort: Int get() = if (settings.port.isNotBlank()) {
        settings.port.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: throw IllegalArgumentException("Port must be 1-65535")
    } else file.getInt("generatedPort", 0).takeIf { it > 0 }
        ?: (20000 + SecureRandom().nextInt(30000)).also { file.edit().putInt("generatedPort", it).apply() }
    val actualUser: String get() = settings.username.ifBlank { generated("generatedUser") }
    val actualPassword: String get() = settings.password.ifBlank { generated("generatedPassword") }

    init {
        val saved = JSONArray(file.getString("subscriptions", "[]") ?: "[]")
        for (i in 0 until saved.length()) saved.getJSONObject(i).let {
            subscriptions += Subscription(it.getString("id"), it.getString("url"), it.optString("title"),
                it.optString("description"), it.optString("userInfo"), it.optString("supportUrl"),
                it.optString("content"), it.optLong("lastUpdated"))
        }
        val savedChain = JSONArray(file.getString("chain", "[]") ?: "[]")
        for (i in 0 until savedChain.length()) chain += savedChain.getString(i)
    }

    fun save() {
        val s = settings
        val settingsJson = JSONObject().put("port", s.port).put("username", s.username).put("password", s.password)
            .put("ipv6", s.ipv6).put("logLevel", s.logLevel).put("userAgent", s.userAgent)
            .put("hwid", s.hwid).put("sendHwid", s.sendHwid).put("tun", s.tun)
            .put("overwriteKey", s.overwriteKey).put("overwriteValue", s.overwriteValue)
        val subs = JSONArray()
        subscriptions.forEach { sub -> subs.put(JSONObject().put("id", sub.id).put("url", sub.url)
            .put("title", sub.title).put("description", sub.description).put("userInfo", sub.userInfo)
            .put("supportUrl", sub.supportUrl).put("content", sub.content).put("lastUpdated", sub.lastUpdated)) }
        file.edit().putString("settings", settingsJson.toString()).putString("subscriptions", subs.toString())
            .putString("chain", JSONArray(chain).toString()).putString("activeId", activeId).apply()
    }

    private fun randomText(): String = buildString {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        repeat(14) { append(alphabet[random.nextInt(alphabet.length)]) }
    }

    private fun generated(key: String): String = file.getString(key, null) ?: randomText().also {
        file.edit().putString(key, it).apply()
    }
}
