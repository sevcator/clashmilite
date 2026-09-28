package org.sevcator.miniclash

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File

object ConfigComposer {
    fun compose(store: Store): String {
        val source = store.subscriptions.firstOrNull { it.id == store.activeId } ?: error("Choose a subscription")
        val imported = SubscriptionCodec.parse(source.content)
        val proxies = imported.proxies
        if (proxies.isEmpty()) error("Subscription has no servers")
        val names = mutableSetOf<String>()
        proxies.forEach { proxy ->
            var name = proxy["name"]?.toString().orEmpty().ifBlank { proxy["server"]?.toString() ?: "Server" }
            val original = name
            var index = 2
            while (!names.add(name)) { name = "$original ($index)"; index++ }
            proxy["name"] = name
        }
        val selected = store.chain.toList().ifEmpty { listOf(proxies.first()["name"].toString()) }
        require(selected.distinct().size == selected.size) { "A server can appear only once in a chain" }
        require(selected.all { it in names }) { "Chain contains a server absent from the active subscription" }
        proxies.forEach { it.remove("dialer-proxy") }
        selected.zipWithNext().forEach { (first, next) ->
            proxies.first { it["name"] == next }["dialer-proxy"] = first
        }
        val root = linkedMapOf<String, Any>(
            "mixed-port" to store.actualPort,
            "allow-lan" to false,
            "authentication" to listOf("${store.actualUser}:${store.actualPassword}"),
            "mode" to "global",
            "log-level" to store.settings.logLevel,
            "ipv6" to store.settings.ipv6,
            "external-controller" to "",
            "dns" to mapOf("enable" to true, "ipv6" to store.settings.ipv6,
                "default-nameserver" to listOf("1.1.1.1", "9.9.9.9"),
                "nameserver" to listOf("1.1.1.1", "9.9.9.9")),
            "proxies" to proxies,
            "proxy-groups" to listOf(mapOf("name" to "GLOBAL", "type" to "select", "proxies" to listOf(selected.last(), "DIRECT"))),
            "rules" to listOf("MATCH,GLOBAL")
        )
        val key = store.settings.overwriteKey.trim()
        if (key.isNotEmpty()) overwrite(root, key, store.settings.overwriteValue)
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
        }
        return Yaml(options).dump(root)
    }

    @Suppress("UNCHECKED_CAST")
    private fun overwrite(value: Any?, key: String, replacement: String) {
        when (value) {
            is MutableMap<*, *> -> {
                val map = value as MutableMap<String, Any>
                if (map.containsKey(key)) map[key] = replacement
                map.values.toList().forEach { overwrite(it, key, replacement) }
            }
            is List<*> -> value.forEach { overwrite(it, key, replacement) }
        }
    }

    fun stage(store: Store, directory: File): File = File(directory, "config.yaml").apply {
        writeText(compose(store), Charsets.UTF_8)
    }
}
