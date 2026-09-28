package org.sevcator.miniclash

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object SubscriptionRepository {
    fun refresh(context: Context, subscription: Subscription, settings: Preferences): ImportedProfile {
        val connection = URL(subscription.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", settings.userAgent.ifBlank { "MiniClash/0.1.1 Android" })
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
        if (settings.sendHwid) {
            val hwid = settings.hwid.ifBlank { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty() }
            connection.setRequestProperty("X-HWID", hwid)
            connection.setRequestProperty("X-Device-ID", hwid)
            connection.setRequestProperty("X-Device-OS", "Android")
            connection.setRequestProperty("X-Ver-OS", Build.VERSION.RELEASE)
            connection.setRequestProperty("X-Device-Model", Build.MODEL)
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = stream.read(chunk)
                    if (count < 0) break
                    output.write(chunk, 0, count)
                    if (output.size() > 4 * 1024 * 1024) error("Subscription exceeds 4 MB")
                }
                output.toByteArray()
            }
            if (bytes.size > 4 * 1024 * 1024) error("Subscription exceeds 4 MB")
            val body = String(bytes, Charsets.UTF_8)
            val parsed = SubscriptionCodec.parse(body)
            fun meta(name: String): String = connection.getHeaderField(name)?.trim().orEmpty().ifBlank {
                Regex("(?m)^#${Regex.escape(name)}:\\s*(.+)$", RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.get(1)?.trim().orEmpty()
            }
            val title = meta("profile-title").ifBlank { meta("subscription-name") }
            if (title.isNotBlank()) {
                val decoded = if (title.startsWith("base64:", true)) runCatching {
                    String(Base64.decode(title.substringAfter(':'), Base64.DEFAULT), Charsets.UTF_8)
                }.getOrDefault(title) else title
                subscription.title = decoded.lineSequence().first().take(80)
                subscription.description = decoded.lineSequence().drop(1).joinToString(" ").take(200)
            }
            subscription.userInfo = meta("subscription-userinfo")
            subscription.supportUrl = meta("support-url")
            subscription.content = body
            subscription.lastUpdated = System.currentTimeMillis()
            return parsed
        } finally { connection.disconnect() }
    }
}
