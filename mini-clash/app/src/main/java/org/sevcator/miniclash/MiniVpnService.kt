package org.sevcator.miniclash

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import io.github.oviron.libmihomo.Clash
import io.github.oviron.libmihomo.TunInterface
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MiniVpnService : VpnService() {
    companion object {
        const val ACTION_START = "org.sevcator.miniclash.START"
        const val ACTION_STOP = "org.sevcator.miniclash.STOP"
        @Volatile var running = false
        @Volatile var lastError = ""
    }
    private val worker = Executors.newSingleThreadExecutor()
    private var vpnFd: Int = -1
    private val tunBridge = object : TunInterface {
        override fun protect(fd: Int) { this@MiniVpnService.protect(fd) }
        override fun resolverProcess(protocol: Int, source: String, target: String, uid: Int): String = ""
    }

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel("tunnel", "Mini Clash connection", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            worker.execute { stopTunnel() }
            return START_NOT_STICKY
        }
        startForeground(1001, notification("Connecting"))
        worker.execute {
            try { startTunnel() } catch (exception: Exception) {
                lastError = exception.message ?: "Unable to connect"
                stopTunnel()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(status: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "tunnel")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Mini Clash")
            .setContentText(status)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun startTunnel() {
        stopCoreOnly()
        val store = Store(this)
        ConfigComposer.stage(store, filesDir)
        Clash.load(applicationInfo.nativeLibraryDir)
        val init = JSONObject().put("home-dir", filesDir.absolutePath).put("version", Build.VERSION.SDK_INT)
        val latch = CountDownLatch(1)
        var setupError = ""
        Clash.quickSetup(init.toString(), "{}") {
            setupError = it.orEmpty()
            latch.countDown()
        }
        if (!latch.await(30, TimeUnit.SECONDS)) throw IllegalStateException("Core initialization timed out")
        if (setupError.isNotEmpty()) throw IllegalStateException(setupError)
        if (store.settings.tun) {
            val builder = Builder().setSession("Mini Clash").setMtu(1400)
                .addAddress("172.19.0.1", 30).addRoute("0.0.0.0", 0).addDnsServer("172.19.0.2")
            if (store.settings.ipv6) builder.addAddress("fdfe:dcba:9876::1", 126)
                .addRoute("::", 0).addDnsServer("fdfe:dcba:9876::2")
            vpnFd = (builder.establish() ?: throw IllegalStateException("Android denied the VPN tunnel")).detachFd()
            Clash.startTUN(vpnFd, tunBridge, "Mini Clash", "system", "172.19.0.1/30", "172.19.0.2", 1400)
        }
        lastError = ""
        running = true
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1001, notification("Connected"))
    }

    private fun stopCoreOnly() {
        if (Clash.isLoaded()) {
            runCatching { Clash.stopTun() }
        }
        vpnFd = -1
        running = false
    }

    private fun stopTunnel() {
        stopCoreOnly()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopCoreOnly(); worker.shutdown(); super.onDestroy() }
}
