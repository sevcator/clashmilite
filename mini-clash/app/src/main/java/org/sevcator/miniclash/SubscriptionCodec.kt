package org.sevcator.miniclash

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

data class ImportedProfile(val proxies: List<MutableMap<String, Any>>, val name: String = "")

object SubscriptionCodec {
    private val schemes = listOf("vless://", "vmess://", "trojan://", "ss://", "hysteria2://", "hy2://", "socks://", "socks5://", "http://", "wireguard://", "wg://", "amneziawg://", "awg://")
    private fun decode(value: String): String {
        val normalized = value.trim().replace('-', '+').replace('_', '/')
        return String(Base64.decode(normalized + "=".repeat((4 - normalized.length % 4) % 4), Base64.DEFAULT), Charsets.UTF_8)
    }

    fun parse(raw: String): ImportedProfile {
        val text = raw.trim().removePrefix("\uFEFF")
        if (text.isEmpty()) throw IllegalArgumentException("Subscription is empty")
        if (text.contains("[Interface]", true) && text.contains("[Peer]", true))
            return unique(ImportedProfile(listOf(parseConf(text))))
        if (text.startsWith("{") || text.startsWith("[")) return unique(parseJson(text))
        if (text.startsWith("proxies:") || text.contains("\nproxies:")) {
            val yaml = Yaml(SafeConstructor(LoaderOptions()))
            val root = yaml.load<Any>(text) as? Map<*, *> ?: error("Invalid Clash profile")
            val proxies = root["proxies"] as? List<*> ?: error("No proxies in Clash profile")
            return unique(ImportedProfile(proxies.mapNotNull { entry ->
                (entry as? Map<*, *>)?.entries?.associate { it.key.toString() to (it.value ?: "") }?.toMutableMap()
            }))
        }
        val plain = if (schemes.none { text.startsWith(it, true) } && !text.contains("://"))
            runCatching { decode(text) }.getOrDefault(text) else text
        if (plain.contains("[Interface]", true) && plain.contains("[Peer]", true))
            return unique(ImportedProfile(listOf(parseConf(plain))))
        val proxies = plain.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (schemes.any { trimmed.startsWith(it, true) }) runCatching { parseLink(trimmed) }.getOrNull() else null
        }
        if (proxies.isEmpty()) throw IllegalArgumentException("No supported servers found in subscription")
        return unique(ImportedProfile(proxies))
    }

    private fun unique(profile: ImportedProfile): ImportedProfile {
        val names = mutableSetOf<String>()
        profile.proxies.forEach { proxy ->
            val base = proxy["name"]?.toString()?.ifBlank { null } ?: proxy["server"]?.toString() ?: "Server"
            var name = base
            var suffix = 2
            while (!names.add(name)) { name = "$base ($suffix)"; suffix++ }
            proxy["name"] = name
        }
        return profile
    }

    private fun parseJson(text: String): ImportedProfile {
        val documents = if (text.startsWith("[")) JSONArray(text) else JSONArray().put(JSONObject(text))
        val proxies = mutableListOf<MutableMap<String, Any>>()
        for (i in 0 until documents.length()) {
            val document = documents.optJSONObject(i) ?: continue
            val clashProxies = document.optJSONArray("proxies")
            if (clashProxies != null) {
                for (j in 0 until clashProxies.length()) {
                    val entry = clashProxies.optJSONObject(j) ?: continue
                    proxies += jsonToMap(entry)
                }
                continue
            }
            val outbounds = document.optJSONArray("outbounds") ?: continue
            for (j in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(j) ?: continue
                runCatching { parseXrayOutbound(outbound) }.getOrNull()?.let { proxies += it }
            }
        }
        if (proxies.isEmpty()) error("No supported servers found in JSON profile")
        return ImportedProfile(proxies)
    }

    private fun jsonToMap(objectValue: JSONObject): MutableMap<String, Any> = mutableMapOf<String, Any>().apply {
        objectValue.keys().forEach { key ->
            val value = objectValue.get(key)
            put(key, when (value) {
                is JSONObject -> jsonToMap(value)
                is JSONArray -> (0 until value.length()).map { index ->
                    val item = value.get(index)
                    if (item is JSONObject) jsonToMap(item) else item
                }
                else -> value
            })
        }
    }

    private fun parseXrayOutbound(outbound: JSONObject): MutableMap<String, Any> {
        val protocol = outbound.optString("protocol").lowercase()
        val settings = outbound.optJSONObject("settings") ?: error("Missing Xray settings")
        val stream = outbound.optJSONObject("streamSettings")
        val endpoint = when (protocol) {
            "vless", "vmess" -> settings.getJSONArray("vnext").getJSONObject(0)
            "trojan", "shadowsocks" -> settings.getJSONArray("servers").getJSONObject(0)
            else -> error("Unsupported Xray protocol")
        }
        val proxy = mutableMapOf<String, Any>(
            "name" to outbound.optString("tag", "$protocol ${endpoint.getString("address")}"),
            "server" to endpoint.getString("address"), "port" to endpoint.getInt("port"),
            "type" to if (protocol == "shadowsocks") "ss" else protocol)
        when (protocol) {
            "vless", "vmess" -> {
                val user = endpoint.getJSONArray("users").getJSONObject(0)
                proxy["uuid"] = user.getString("id")
                if (protocol == "vless") proxy["flow"] = user.optString("flow")
                else { proxy["alterId"] = user.optInt("alterId", 0); proxy["cipher"] = user.optString("security", "auto") }
            }
            "trojan" -> proxy["password"] = endpoint.getString("password")
            "shadowsocks" -> { proxy["password"] = endpoint.getString("password"); proxy["cipher"] = endpoint.getString("method") }
        }
        proxy["udp"] = true
        val security = stream?.optString("security").orEmpty()
        if (security == "tls" || security == "reality") proxy["tls"] = true
        if (security == "reality") stream?.optJSONObject("realitySettings")?.let {
            proxy["reality-opts"] = mapOf("public-key" to it.optString("publicKey"), "short-id" to it.optString("shortId"))
            if (it.optString("fingerprint").isNotBlank()) proxy["client-fingerprint"] = it.getString("fingerprint")
            if (it.optString("serverName").isNotBlank()) proxy["servername"] = it.getString("serverName")
        }
        if (security == "tls") stream?.optJSONObject("tlsSettings")?.let {
            if (it.optString("serverName").isNotBlank()) proxy["servername"] = it.getString("serverName")
            if (it.optString("fingerprint").isNotBlank()) proxy["client-fingerprint"] = it.getString("fingerprint")
            if (it.optBoolean("allowInsecure")) proxy["skip-cert-verify"] = true
        }
        stream?.optString("network")?.takeIf { it.isNotBlank() && it != "tcp" }?.let { network ->
            proxy["network"] = network
            if (network == "ws") stream?.optJSONObject("wsSettings")?.let {
                proxy["ws-opts"] = mapOf("path" to it.optString("path", "/"),
                    "headers" to jsonToMap(it.optJSONObject("headers") ?: JSONObject()))
            }
            if (network == "grpc") stream?.optJSONObject("grpcSettings")?.let {
                proxy["grpc-opts"] = mapOf("grpc-service-name" to it.optString("serviceName"))
            }
        }
        return proxy
    }

    private fun parseLink(link: String): MutableMap<String, Any> {
        val scheme = link.substringBefore("://").lowercase()
        if (scheme == "amneziawg" || scheme == "awg") {
            val payload = link.substringAfter("://").substringBefore('#')
            val name = link.substringAfter('#', "AmneziaWG")
            return parseConf(decode(payload), Uri.decode(name))
        }
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
        if (scheme == "hysteria2" || scheme == "hy2") {
            query["obfs"]?.takeIf { it.isNotBlank() }?.let { proxy["obfs"] = it }
            (query["obfs-password"] ?: query["obfsParam"])?.takeIf { it.isNotBlank() }?.let { proxy["obfs-password"] = it }
            query["mport"]?.takeIf { it.isNotBlank() }?.let { proxy["ports"] = it }
            query["hop-interval"]?.toIntOrNull()?.let { proxy["hop-interval"] = it }
            query["upmbps"]?.toIntOrNull()?.let { proxy["up"] = "$it Mbps" }
            query["downmbps"]?.toIntOrNull()?.let { proxy["down"] = "$it Mbps" }
        }
        val security = query["security"].orEmpty().lowercase()
        if (security == "tls" || security == "reality" || scheme == "trojan") proxy["tls"] = true
        if (security == "reality") proxy["reality-opts"] = mapOf(
            "public-key" to (query["pbk"] ?: query["publicKey"] ?: ""), "short-id" to (query["sid"] ?: ""))
        (query["sni"] ?: query["servername"])?.takeIf { it.isNotBlank() }?.let {
            proxy[if (scheme == "hysteria2" || scheme == "hy2") "sni" else "servername"] = it
        }
        (query["fp"] ?: query["fingerprint"])?.takeIf { it.isNotBlank() }?.let { proxy["client-fingerprint"] = it }
        if (security == "reality" && !proxy.containsKey("client-fingerprint")) proxy["client-fingerprint"] = "chrome"
        query["alpn"]?.takeIf { it.isNotBlank() }?.let { proxy["alpn"] = it.split(',') }
        if (query["allowInsecure"] == "1" || query["insecure"] == "1") proxy["skip-cert-verify"] = true
        val network = query["type"].orEmpty()
        if (network.isNotBlank() && network != "tcp") proxy["network"] = network
        if (network == "ws") proxy["ws-opts"] = mapOf("path" to (query["path"] ?: "/"),
            "headers" to mapOf("Host" to (query["host"] ?: "")))
        if (network == "grpc") proxy["grpc-opts"] = mapOf("grpc-service-name" to (query["serviceName"] ?: ""))
        return proxy
    }

    private fun parseConf(text: String, label: String = "WireGuard"): MutableMap<String, Any> {
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        var section = ""
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.startsWith('[') && line.endsWith(']')) {
                section = line.removePrefix("[").removeSuffix("]").lowercase()
                sections.getOrPut(section) { mutableMapOf() }
            } else if (line.contains('=') && section.isNotBlank()) {
                sections.getValue(section)[line.substringBefore('=').trim().lowercase()] = line.substringAfter('=').trim()
            }
        }
        val iface = sections["interface"] ?: error("WireGuard interface missing")
        val peer = sections["peer"] ?: error("WireGuard peer missing")
        val endpoint = peer["endpoint"] ?: error("WireGuard endpoint missing")
        val host = endpoint.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
        val port = endpoint.substringAfterLast(':').toIntOrNull()?.takeIf { it in 1..65535 }
            ?: error("Invalid WireGuard port")
        val addresses = iface["address"]?.split(',')?.map { it.trim().substringBefore('/') }
            ?: error("WireGuard address missing")
        val proxy = mutableMapOf<String, Any>(
            "name" to label, "type" to "wireguard", "server" to host, "port" to port,
            "private-key" to (iface["privatekey"] ?: error("WireGuard private key missing")),
            "public-key" to (peer["publickey"] ?: error("WireGuard public key missing")),
            "ip" to (addresses.firstOrNull { ':' !in it } ?: error("WireGuard IPv4 address missing")),
            "udp" to true)
        addresses.firstOrNull { ':' in it }?.let { proxy["ipv6"] = it }
        peer["presharedkey"]?.let { proxy["pre-shared-key"] = it }
        peer["persistentkeepalive"]?.toIntOrNull()?.let { proxy["persistent-keepalive"] = it }
        iface["mtu"]?.toIntOrNull()?.let { proxy["mtu"] = it }
        peer["reserved"]?.let { proxy["reserved"] = it.split(',').mapNotNull { value -> value.trim().toIntOrNull() } }
        val config = iface + (sections["device"] ?: emptyMap())
        val fields = listOf("jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4",
            "j1", "j2", "j3", "itime",
            "i1", "i2", "i3", "i4", "i5", "headerprotectionkey", "contentpaddingaddition", "rekeyaftertime",
            "rekeytimeout", "rejectaftertime", "keepalivetimeout", "maxhandshakeattempts", "randomtrailers", "disablecookies")
        if (fields.any { config.containsKey(it) }) {
            val option = mutableMapOf<String, Any>()
            val v3 = config.keys.any { it in listOf("headerprotectionkey", "contentpaddingaddition", "rekeyaftertime",
                "rekeytimeout", "rejectaftertime", "keepalivetimeout", "maxhandshakeattempts", "randomtrailers", "disablecookies") }
            if (v3 || config["version"] == "3") option["version"] = 3
            fields.forEach { field ->
                val value = config[field] ?: return@forEach
                val outputKey = when (field) {
                    "headerprotectionkey" -> "header-protection-key"
                    "contentpaddingaddition" -> "content-padding-addition"
                    "rekeyaftertime" -> "rekey-after-time"
                    "rekeytimeout" -> "rekey-timeout"
                    "rejectaftertime" -> "reject-after-time"
                    "keepalivetimeout" -> "keepalive-timeout"
                    "maxhandshakeattempts" -> "max-handshake-attempts"
                    "randomtrailers" -> "random-trailers"
                    "disablecookies" -> "disable-cookies"
                    else -> field
                }
                option[outputKey] = when (field) {
                    "jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "itime" -> value.toIntOrNull() ?: error("Invalid $field")
                    "randomtrailers", "disablecookies" -> value.lowercase() in listOf("true", "yes", "on", "enabled", "1")
                    else -> value
                }
            }
            proxy["amnezia-wg-option"] = option
        }
        return proxy
    }
}
