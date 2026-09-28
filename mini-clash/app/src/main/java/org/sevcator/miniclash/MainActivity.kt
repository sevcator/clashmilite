package org.sevcator.miniclash

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val canvasColor = Color.rgb(12, 17, 24)
    private val surface = Color.rgb(25, 32, 43)
    private val inkColor = Color.rgb(237, 243, 249)
    private val muted = Color.rgb(148, 164, 181)
    private val accent = Color.rgb(72, 219, 181)
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private var page = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = canvasColor
        window.navigationBarColor = canvasColor
        store = Store(this)
        render()
        importIntent(intent)
        store.subscriptions.firstOrNull { it.id == store.activeId && it.url.isNotBlank() && System.currentTimeMillis() - it.lastUpdated > 6 * 60 * 60 * 1000L }
            ?.let { refresh(it, false) }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); importIntent(intent) }

    private fun importIntent(intent: Intent?) {
        val input = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        if (input.startsWith("incy://crypt1/")) {
            val decoded = runCatching { IncyCrypt1.decode(input) }
                .getOrElse { return message(it.message ?: "Cannot decode INCY link") }
            AlertDialog.Builder(this).setTitle("Import subscription")
                .setMessage("Add ${decoded.second.ifBlank { "this provider" }} to Mini Clash?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add") { _, _ -> addInput(decoded.first) }.show()
            return
        }
        val unwrapped = when {
            input.startsWith("happ://add/") -> input.removePrefix("happ://add/")
            input.startsWith("incy://add/") -> input.removePrefix("incy://add/")
            input.startsWith("incy://import/") -> input.removePrefix("incy://import/")
            else -> input
        }
        showAdd(unwrapped)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 18) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float = 16f, color: Int = inkColor, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun spacer(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun card(): LinearLayout = column().apply {
        background = shape(surface)
        setPadding(dp(18), dp(17), dp(18), dp(17))
    }
    private fun addCard(view: View) {
        root.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    private fun action(label: String, primary: Boolean = false, click: () -> Unit) = Button(this).apply {
        text = label; textSize = 14f; isAllCaps = false
        setTextColor(if (primary) canvasColor else inkColor)
        background = shape(if (primary) accent else Color.rgb(45, 56, 69), 13)
        setOnClickListener { click() }
    }

    private fun render() {
        val outer = column().apply { setBackgroundColor(canvasColor) }
        setContentView(outer)
        val header = column().apply { setPadding(dp(22), dp(21), dp(22), dp(11)) }
        header.addView(text("MINI CLASH", 13f, accent, true))
        header.addView(text(when (page) { "settings" -> "Settings"; "subscriptions" -> "Subscriptions"; else -> "Your connection" }, 27f, inkColor, true))
        outer.addView(header)
        val scroll = ScrollView(this)
        root = column().apply { setPadding(dp(18), dp(8), dp(18), dp(18)) }
        scroll.addView(root)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when (page) {
            "settings" -> settingsPage()
            "subscriptions" -> subscriptionsPage()
            else -> homePage()
        }
        val nav = LinearLayout(this).apply {
            setPadding(dp(12), dp(8), dp(12), dp(10))
            setBackgroundColor(surface)
        }
        listOf("home" to "Home", "subscriptions" to "Profiles", "settings" to "Settings").forEach { (destination, label) ->
            val item = text(label, 15f, if (destination == page) accent else muted, destination == page).apply {
                gravity = Gravity.CENTER; setPadding(dp(6), dp(12), dp(6), dp(12))
                setOnClickListener { store.save(); page = destination; render() }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        outer.addView(nav)
    }

    private fun homePage() {
        val subscription = store.subscriptions.firstOrNull { it.id == store.activeId }
        val status = card().apply {
            addView(text(if (MiniVpnService.running) "CONNECTED" else "DISCONNECTED", 12f, if (MiniVpnService.running) accent else muted, true))
            addView(spacer(8))
            addView(text(subscription?.title ?: "Add a subscription", 22f, inkColor, true))
            addView(spacer(5))
            addView(text(if (subscription == null) "Import a server or subscription to begin" else
                "${runCatching { SubscriptionCodec.parse(subscription.content).proxies.size }.getOrDefault(0)} servers available", 13f, muted))
            addView(spacer(15))
            addView(action(if (MiniVpnService.running) "Disconnect" else "Connect", true) {
                if (MiniVpnService.running) startService(Intent(this@MainActivity, MiniVpnService::class.java).setAction(MiniVpnService.ACTION_STOP))
                else connect()
                handler.postDelayed({ render() }, 750)
            })
        }
        addCard(status)
        if (MiniVpnService.lastError.isNotBlank()) addCard(card().apply { addView(text(MiniVpnService.lastError, 13f, Color.rgb(255, 147, 143))) })
        val chainCard = card()
        chainCard.addView(text("ROUTE", 12f, muted, true))
        chainCard.addView(spacer(12))
        val route = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        route.addView(text("GLOBAL", 16f, accent, true))
        store.chain.forEachIndexed { index, hop ->
            route.addView(text("  ->  ", 17f, muted))
            route.addView(action(hop) { chooseReplacement(index) })
        }
        if (store.chain.isEmpty()) route.addView(text("  ->  Choose a server", 16f, muted))
        chainCard.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(route) })
        chainCard.addView(spacer(13))
        chainCard.addView(action("+  Add next hop") { chooseHop() })
        if (store.chain.isNotEmpty()) chainCard.addView(action("Remove last hop") {
            store.chain.removeAt(store.chain.lastIndex); store.save(); render()
        })
        addCard(chainCard)
        addCard(card().apply {
            addView(text("LOCAL PROXY", 12f, muted, true)); addView(spacer(5))
            addView(text("127.0.0.1:${store.actualPort}", 17f, inkColor, true))
            addView(text("Port and credentials are randomized when left blank", 12f, muted))
            addView(spacer(8))
            addView(action("Show credentials") {
                AlertDialog.Builder(this@MainActivity).setTitle("Local proxy access")
                    .setMessage("Username: ${store.actualUser}\nPassword: ${store.actualPassword}")
                    .setPositiveButton("Done", null).show()
            })
        })
    }

    private fun subscriptionsPage() {
        addCard(action("+  Add subscription or server", true) { showAdd("") })
        store.subscriptions.forEach { sub ->
            addCard(card().apply {
                addView(text(sub.title.ifBlank { "Subscription" }, 19f, inkColor, true))
                if (sub.description.isNotBlank()) addView(text(sub.description, 13f, muted))
                val count = runCatching { SubscriptionCodec.parse(sub.content).proxies.size }.getOrDefault(0)
                addView(text("$count servers" + if (sub.id == store.activeId) "  |  Active" else "", 13f, accent))
                val servers = runCatching { SubscriptionCodec.parse(sub.content).proxies.map { it["name"].toString() } }.getOrDefault(emptyList())
                servers.take(3).forEach { name -> addView(text("  $name", 13f, muted)) }
                if (servers.size > 3) addView(text("  +${servers.size - 3} more", 12f, muted))
                if (sub.userInfo.isNotBlank()) {
                    addView(spacer(8))
                    addView(text(formatUserInfo(sub.userInfo), 12f, muted))
                    val values = sub.userInfo.split(';').mapNotNull { item ->
                        item.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
                    }.toMap()
                    val total = values["total"]?.toLongOrNull() ?: 0L
                    val used = (values["upload"]?.toLongOrNull() ?: 0L) + (values["download"]?.toLongOrNull() ?: 0L)
                    if (total > 0) addView(ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100; progress = (used * 100 / total).coerceIn(0, 100).toInt()
                        progressTintList = android.content.res.ColorStateList.valueOf(accent)
                    })
                }
                addView(spacer(10))
                addView(action("Use") { store.activeId = sub.id; store.chain.clear(); store.save(); page = "home"; render() })
                if (servers.isNotEmpty()) addView(action("Choose server") {
                    AlertDialog.Builder(this@MainActivity).setTitle(sub.title).setItems(servers.toTypedArray()) { _, index ->
                        store.activeId = sub.id; store.chain.clear(); store.chain += servers[index]
                        store.save(); page = "home"; render()
                    }.show()
                })
                if (sub.url.isNotBlank()) addView(action("Refresh") { refresh(sub) })
                if (sub.supportUrl.startsWith("https://")) addView(action("Provider support") {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(sub.supportUrl)))
                })
                addView(action("Delete") {
                    AlertDialog.Builder(this@MainActivity).setMessage("Delete ${sub.title}?").setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            store.subscriptions.remove(sub)
                            if (store.activeId == sub.id) { store.activeId = ""; store.chain.clear() }
                            store.save(); render()
                        }.show()
                })
            })
        }
    }

    private fun formatUserInfo(raw: String): String {
        val values = raw.split(';').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        val total = values["total"]?.toLongOrNull() ?: 0L
        val used = (values["upload"]?.toLongOrNull() ?: 0L) + (values["download"]?.toLongOrNull() ?: 0L)
        val traffic = if (total > 0) "%.1f / %.1f GB".format(used / 1e9, total / 1e9) else "Traffic: unlimited"
        val expiry = values["expire"]?.toLongOrNull()?.takeIf { it > 0 }?.let {
            " | Expires " + java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it * 1000))
        }.orEmpty()
        return traffic + expiry
    }

    private fun showAdd(initial: String) {
        val input = EditText(this).apply {
            setText(initial); hint = "https://... or vless://..."; minLines = 2; maxLines = 8
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        AlertDialog.Builder(this).setTitle("Add to Mini Clash").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Add") { _, _ -> addInput(input.text.toString().trim()) }.show()
    }

    private fun addInput(input: String) {
        if (input.isBlank()) return
        if (input.startsWith("incy://crypt1/")) {
            val decoded = runCatching { IncyCrypt1.decode(input).first }
                .getOrElse { return message(it.message ?: "Cannot decode INCY link") }
            addInput(decoded)
            return
        }
        if (input.startsWith("happ://crypt", true)) {
            message("This Happ encrypted link needs its provider's decoder. Ask for a direct subscription URL.")
            return
        }
        val sub = Subscription(UUID.randomUUID().toString(), if (input.startsWith("https://")) input else "", "New subscription")
        if (sub.url.isNotBlank()) {
            store.subscriptions += sub; store.activeId = sub.id; store.save(); refresh(sub)
        } else try {
            val parsed = SubscriptionCodec.parse(input)
            sub.title = "Local profile"; sub.content = input
            store.subscriptions += sub; store.activeId = sub.id; store.chain.clear(); store.chain += parsed.proxies.first()["name"].toString()
            store.save(); page = "home"; render()
        } catch (exception: Exception) { message(exception.message ?: "Import failed") }
    }

    private fun refresh(sub: Subscription, announce: Boolean = true) {
        if (announce) message("Refreshing ${sub.title}")
        worker.execute {
            try {
                val parsed = SubscriptionRepository.refresh(this, sub, store.settings)
                runOnUiThread {
                    if (store.activeId == sub.id) {
                        store.chain.retainAll(parsed.proxies.map { it["name"].toString() }.toSet())
                        if (store.chain.isEmpty()) store.chain += parsed.proxies.first()["name"].toString()
                    }
                    store.save(); render()
                }
            } catch (exception: Exception) { runOnUiThread { message(exception.message ?: "Refresh failed") } }
        }
    }

    private fun chooseHop() {
        val sub = store.subscriptions.firstOrNull { it.id == store.activeId } ?: return message("Add a subscription first")
        val servers = runCatching { SubscriptionCodec.parse(sub.content).proxies.map { it["name"].toString() } }
            .getOrElse { return message(it.message ?: "Profile unavailable") }
            .filterNot { it in store.chain }
        if (servers.isEmpty()) return message("No more servers available")
        AlertDialog.Builder(this).setTitle("Choose next hop").setItems(servers.toTypedArray()) { _, index ->
            store.chain += servers[index]; store.save(); render()
        }.show()
    }

    private fun chooseReplacement(index: Int) {
        val sub = store.subscriptions.firstOrNull { it.id == store.activeId } ?: return
        val choices = runCatching { SubscriptionCodec.parse(sub.content).proxies.map { it["name"].toString() } }
            .getOrElse { return message(it.message ?: "Profile unavailable") }
            .filter { it == store.chain[index] || it !in store.chain }
        AlertDialog.Builder(this).setTitle("Change hop ${index + 1}").setItems(choices.toTypedArray()) { _, choice ->
            store.chain[index] = choices[choice]; store.save(); render()
        }.show()
    }

    private fun connect() {
        try {
            ConfigComposer.compose(store)
            if (store.settings.tun) {
                val permission = VpnService.prepare(this)
                if (permission != null) { startActivityForResult(permission, 501); return }
            }
            launchService()
        } catch (exception: Exception) { message(exception.message ?: "Cannot connect") }
    }

    @Deprecated("Android VPN consent API uses activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 501 && resultCode == RESULT_OK) launchService()
    }

    private fun launchService() {
        MiniVpnService.lastError = ""
        startForegroundService(Intent(this, MiniVpnService::class.java).setAction(MiniVpnService.ACTION_START))
        handler.postDelayed({ render() }, 1500)
    }

    private fun settingsPage() {
        val s = store.settings
        addCard(card().apply {
            addView(text("CONNECTION", 12f, accent, true)); addView(spacer(10))
            field("Port", s.port, "randomized") { s.port = it.filter(Char::isDigit).take(5) }
            field("Proxy username", s.username, "randomized") { s.username = it }
            field("Proxy password", s.password, "randomized") { s.password = it }
            toggle("IPv6", s.ipv6) { s.ipv6 = it }
            toggle("TUN (device VPN)", s.tun) { s.tun = it }
        })
        addCard(card().apply {
            addView(text("SUBSCRIPTIONS", 12f, accent, true)); addView(spacer(10))
            field("User Agent", s.userAgent, "MiniClash/0.1.0 Android") { s.userAgent = it }
            field("HWID override", s.hwid, "Android ID by default") { s.hwid = it }
            toggle("Send HWID to subscription", s.sendHwid) { s.sendHwid = it }
            val deviceHwid = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID).orEmpty()
            addView(text("Device HWID: $deviceHwid", 12f, muted))
            addView(action("Copy device HWID") {
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Mini Clash HWID", deviceHwid))
                message("HWID copied")
            })
        })
        addCard(card().apply {
            addView(text("PROFILE", 12f, accent, true)); addView(spacer(10))
            field("Overwrite key", s.overwriteKey, "client-fingerprint") { s.overwriteKey = it }
            field("Overwrite value", s.overwriteValue, "qq") { s.overwriteValue = it }
            addView(text("Replaces every matching key in the generated profile.", 12f, muted))
            addView(spacer(12))
            addView(text("Log level", 14f, inkColor, true))
            addView(action(if (s.logLevel == "silent") "OFF" else s.logLevel.uppercase()) {
                val levels = arrayOf("Off", "Error", "Warning", "Info", "Debug")
                AlertDialog.Builder(this@MainActivity).setTitle("Log level").setItems(levels) { _, index ->
                    s.logLevel = if (index == 0) "silent" else levels[index].lowercase()
                    store.save(); render()
                }.show()
            })
        })
        addCard(action("Save settings", true) {
            if (s.port.isNotBlank() && s.port.toIntOrNull()?.let { it in 1..65535 } != true) return@action message("Port must be 1-65535")
            store.save(); message("Settings saved")
        })
    }

    private fun LinearLayout.field(label: String, value: String, placeholder: String, changed: (String) -> Unit) {
        addView(text(label, 14f, inkColor, true))
        val input = EditText(this@MainActivity).apply {
            setText(value); hint = placeholder; textSize = 15f; setSingleLine(true)
            setTextColor(inkColor); setHintTextColor(muted)
            backgroundTintList = android.content.res.ColorStateList.valueOf(muted)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    changed(s.toString()); store.save()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        addView(input, LinearLayout.LayoutParams(-1, dp(52)))
        addView(spacer(9))
    }

    private fun LinearLayout.toggle(label: String, value: Boolean, changed: (Boolean) -> Unit) {
        val toggle = Switch(this@MainActivity).apply {
            text = label; textSize = 15f; setTextColor(inkColor); isChecked = value
            setOnCheckedChangeListener { _, checked -> changed(checked); store.save() }
        }
        addView(toggle, LinearLayout.LayoutParams(-1, dp(52)))
    }

    private fun message(value: String) = android.widget.Toast.makeText(this, value, android.widget.Toast.LENGTH_LONG).show()
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
