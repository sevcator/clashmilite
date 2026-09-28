package org.sevcator.miniclash

import android.net.Uri
import android.util.Base64
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.net.URLDecoder

data class ImportedProfile(val proxies: List<MutableMap<String, Any>>, val name: String = "")

object SubscriptionCodec {
    private val schemes = listOf("vless://", "vmess://", "trojan://", "ss://", "hysteria2://", "hy2://", "socks://", "socks5://", "http://", "wireguard://", "wg://")
    private fun decode(value: String): String {
        val normalized = value.trim().replace('-', '+').replace('_', '/')
        return String(Base64.decode(normalized + "=".repeat((4 - normalized.length % 4) % 4), Base64.DEFAULT), Charsets.UTF_8)
    }

    fun parse(raw: String): ImportedProfile {
        val text = raw.trim().removePrefix("\uFEFF")
        if (text.isEmpty()) throw IllegalArgumentException("Subscription is empty")
        if (text.startsWith("proxies:") || text.contains("\nproxies:")) {
            val yaml = Yaml(SafeConstructor(LoaderOptions()))
            val root = yaml.load<Any>(text) as? Map<*, *> ?: error("Invalid Clash profile")
            val proxies = root["proxies"] as? List<*> ?: error("No proxies in Clash profile")
            return ImportedProfile(proxies.mapNotNull { entry ->
                (entry as? Map<*, *>)?.entries?.associate { it.key.toString() to (it.value ?: "") }?.toMutableMap()
            })
        }
        val plain = if (schemes.none { text.startsWith(it, true) } && !text.contains("://"))
            runCatching { decode(text) }.getOrDefault(text) else text
        val proxies = plain.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (schemes.any { trimmed.startsWith(it, true) }) runCatching { parseLink(trimmed) }.getOrNull() else null
        }
        if (proxies.isEmpty()) throw IllegalArgumentException("No supported servers found in subscription")
        return ImportedProfile(proxies)
    }

    private fun parseLink(link: String): MutableMap<String, Any> {
        val scheme = link.substringBefore("://").lowercase()
        if (scheme == "vmess") {
            val json = org.json.JSONObject(decode(link.substringAfter("://")))
            val proxy = mutableMapOf<String, Any>("name" to json.optString("ps", "VMess"), "type" to "vmess",
                "server" to json.getString("add"), "port" to json.getInt("port"), "uuid" to json.getString("id"),
                "alterId" to json.optInt("aid", 0), "cipher" to json.optString("scy", "auto"))
            if (json.optString("tls") == "tls") proxy["tls"] = true
            json.optString("sni").takeIf { it.isNotBlank() }?.let { proxy["servername"] = it }
            json.optString("net").takeIf { it.isNotBlank() && it != "tcp" }?.let { proxy["network"] = it }
            if (json.optString("net") == "ws") proxy["ws-opts"] = mapOf("path" to json.optString("path", "/"),
                "headers" to mapOf("Host" to json.optString("host")))
            return proxy
        }
        val uri = Uri.parse(link)
        val server = uri.host ?: error("Missing server host")
        val port = uri.port.takeIf { it in 1..65535 } ?: error("Invalid server port")
        val name = Uri.decode(uri.fragment ?: "$scheme $server").ifBlank { "$scheme $server" }
        val userInfo = uri.encodedAuthority?.substringBefore('@')?.let { Uri.decode(it) } ?: ""
        val query = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
        val proxy = mutableMapOf<String, Any>("name" to name, "server" to server, "port" to port)
        when (scheme) {
            "vless" -> { proxy["type"] = "vless"; proxy["uuid"] = userInfo; proxy["udp"] = true }
            "trojan" -> { proxy["type"] = "trojan"; proxy["password"] = userInfo; proxy["udp"] = true }
            "hysteria2", "hy2" -> { proxy["type"] = "hysteria2"; proxy["password"] = userInfo; proxy["udp"] = true }
            "socks", "socks5" -> {
                proxy["type"] = "socks5"
                if (userInfo.contains(':')) { proxy["username"] = userInfo.substringBefore(':'); proxy["password"] = userInfo.substringAfter(':') }
            }
            "http" -> {
                require(userInfo.contains(':')) { "HTTP proxy link needs username:password" }
                proxy["type"] = "http"
                proxy["username"] = userInfo.substringBefore(':')
                proxy["password"] = userInfo.substringAfter(':')
            }
            "ss" -> {
                proxy["type"] = "ss"; proxy["udp"] = true
                val credential = if (userInfo.contains(':')) userInfo else decode(userInfo)
                proxy["cipher"] = credential.substringBefore(':'); proxy["password"] = credential.substringAfter(':')
            }
            "wireguard", "wg" -> {
                proxy["type"] = "wireguard"
                proxy["private-key"] = userInfo
                proxy["public-key"] = query["publickey"] ?: query["public-key"] ?: error("WireGuard public key missing")
                val addresses = query["address"]?.split(',')?.map { it.trim().substringBefore('/') }
                    ?: error("WireGuard address missing")
                proxy["ip"] = addresses.firstOrNull { ':' !in it } ?: error("WireGuard IPv4 address missing")
                addresses.firstOrNull { ':' in it }?.let { proxy["ipv6"] = it }
                query["reserved"]?.split(',')?.mapNotNull { it.toIntOrNull() }?.let { proxy["reserved"] = it }
                return proxy
            }
            else -> error("Unsupported scheme: $scheme")
        }
        if (scheme == "vless") {
            proxy["flow"] = query["flow"].orEmpty()
            if (query["encryption"].orEmpty() !in listOf("", "none")) error("Unsupported VLESS encryption")
        }
        val security = query["security"].orEmpty().lowercase()
        if (security == "tls" || security == "reality" || scheme == "trojan") proxy["tls"] = true
        if (security == "reality") proxy["reality-opts"] = mapOf(
            "public-key" to (query["pbk"] ?: query["publicKey"] ?: ""), "short-id" to (query["sid"] ?: ""))
        (query["sni"] ?: query["servername"])?.takeIf { it.isNotBlank() }?.let { proxy["servername"] = it }
        (query["fp"] ?: query["fingerprint"])?.takeIf { it.isNotBlank() }?.let { proxy["client-fingerprint"] = it }
        query["alpn"]?.takeIf { it.isNotBlank() }?.let { proxy["alpn"] = it.split(',') }
        if (query["allowInsecure"] == "1" || query["insecure"] == "1") proxy["skip-cert-verify"] = true
        val network = query["type"].orEmpty()
        if (network.isNotBlank() && network != "tcp") proxy["network"] = network
        if (network == "ws") proxy["ws-opts"] = mapOf("path" to (query["path"] ?: "/"),
            "headers" to mapOf("Host" to (query["host"] ?: "")))
        if (network == "grpc") proxy["grpc-opts"] = mapOf("grpc-service-name" to (query["serviceName"] ?: ""))
        return proxy
    }
}
