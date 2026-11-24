package com.nfckeywallet.app.nfc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.nfckeywallet.app.MainActivity
import com.nfckeywallet.app.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shizuku-style Foreground Service for frictionless Wireless Debugging pairing (Android 11+).
 * Watches mDNS for _adb-tls-pairing._tcp. and _adb-tls-connect._tcp. in the background while
 * the user stays inside Android Settings -> Wireless debugging -> Pair device with pairing code.
 * The moment the pairing port appears, posts a heads-up notification with inline RemoteInput so
 * the user only types the 6 digits — zero split-screen and zero manual port entry.
 */
class WirelessAdbPairingService : Service() {

    private var nsdManager: NsdManager? = null
    private var pairingListener: NsdManager.DiscoveryListener? = null
    private var connectListener: NsdManager.DiscoveryListener? = null

    private val discoveredPairingPort = AtomicInteger(0)
    private val discoveredConnectPort = AtomicInteger(0)
    private val executor = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()

        when (intent?.action) {
            ACTION_STOP_PAIRING -> {
                stopDiscovery()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_SUBMIT_CODE -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val rawCode = results?.getCharSequence(KEY_PAIRING_CODE)?.toString().orEmpty()
                val cleanCode = rawCode.filter { it.isDigit() }
                val port = discoveredPairingPort.get()
                if (cleanCode.length != 6) {
                    notifyPromptForCode(
                        port = port,
                        errorHint = "Нужно ровно 6 цифр (получено: ${cleanCode.length})"
                    )
                    return START_NOT_STICKY
                }
                handleCodeSubmission(cleanCode, port)
                return START_NOT_STICKY
            }

            else -> {
                discoveredPairingPort.set(0)
                discoveredConnectPort.set(0)
                startInForeground(buildSearchingNotification())
                broadcastStatus(false, "Ищу службу «Отладка по Wi-Fi»... Откройте «Подключить устройство по коду».")
                startMdnsDiscovery()
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopDiscovery()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMdnsDiscovery() {
        stopDiscovery()
        val nsd = applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = nsd

        pairingListener = createDiscoveryListener(isPairing = true)
        connectListener = createDiscoveryListener(isPairing = false)

        runCatching {
            nsd.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, pairingListener)
        }
        runCatching {
            nsd.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, connectListener)
        }
    }

    private fun createDiscoveryListener(isPairing: Boolean): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val nsd = nsdManager ?: return
                runCatching {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            if (si.port <= 0) return
                            if (isPairing) {
                                val prev = discoveredPairingPort.getAndSet(si.port)
                                if (prev != si.port) {
                                    notifyPromptForCode(si.port, errorHint = null)
                                    broadcastStatus(
                                        false,
                                        "Порт сопряжения найден (:${si.port})! Введите 6 цифр в уведомлении сверху."
                                    )
                                }
                            } else {
                                discoveredConnectPort.set(si.port)
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
    }

    private fun stopDiscovery() {
        val nsd = nsdManager ?: return
        pairingListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        connectListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        pairingListener = null
        connectListener = null
    }

    private fun handleCodeSubmission(code: String, pairingPort: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildWorkingNotification())
        broadcastStatus(false, "Сопряжение и выдача WRITE_SECURE_SETTINGS...")

        executor.execute {
            val (ok, msg) = WirelessAdbHelper.pairAndGrantPermission(
                context = applicationContext,
                pairingPortInput = pairingPort,
                pairingCode = code,
                connectPortInput = discoveredConnectPort.get()
            )
            stopDiscovery()
            broadcastStatus(ok, msg)
            nm.notify(RESULT_NOTIFICATION_ID, buildResultNotification(ok, msg))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun broadcastStatus(granted: Boolean, message: String) {
        val intent = Intent(ACTION_PAIRING_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_GRANTED, granted)
            putExtra(EXTRA_MESSAGE, message)
        }
        sendBroadcast(intent)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Настройка прав по Wi-Fi",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Позволяет ввести 6-значный код Отладки по Wi-Fi прямо из уведомления"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildSearchingNotification(): Notification {
        val cancelIntent = Intent(this, WirelessAdbPairingService::class.java).apply {
            action = ACTION_STOP_PAIRING
        }
        val cancelPending = PendingIntent.getService(
            this,
            101,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_nfc_key)
            .setContentTitle("mifareRill: жду окно кода Wi-Fi")
            .setContentText("Нажмите «Отладка по Wi-Fi» → «Подключить по коду» — поле ввода появится здесь")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Включите «Отладка по Wi-Fi» и нажмите «Подключить устройство по коду». Порт найдётся сам, и здесь появится поле для 6 цифр."
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Отмена", cancelPending)
            .build()
    }

    private fun notifyPromptForCode(port: Int, errorHint: String?) {
        val replyIntent = Intent(this, WirelessAdbPairingService::class.java).apply {
            action = ACTION_SUBMIT_CODE
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPending = PendingIntent.getService(this, 102, replyIntent, flags)

        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("6-значный код Wi-Fi")
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_qs_nfc_key,
            "Ввести 6 цифр",
            replyPending
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val cancelIntent = Intent(this, WirelessAdbPairingService::class.java).apply {
            action = ACTION_STOP_PAIRING
        }
        val cancelPending = PendingIntent.getService(
            this,
            101,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = errorHint ?: "Служба найдена (порт $port). Нажмите «Ввести 6 цифр» ниже и отправьте код."

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_nfc_key)
            .setContentTitle("Служба Wi-Fi найдена! Введите код")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(replyAction)
            .addAction(0, "Отмена", cancelPending)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildWorkingNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_nfc_key)
            .setContentTitle("mifareRill: выдаю право...")
            .setContentText("Выполняю сопряжение и pm grant WRITE_SECURE_SETTINGS")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun buildResultNotification(ok: Boolean, message: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openAppPending = PendingIntent.getActivity(
            this,
            103,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qs_nfc_key)
            .setContentTitle(if (ok) "Готово! Право выдано ✓" else "Не удалось выдать право")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openAppPending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "wireless_adb_pairing"
        const val NOTIFICATION_ID = 3101
        const val RESULT_NOTIFICATION_ID = 3102

        const val ACTION_START_PAIRING = "com.nfckeywallet.app.action.START_WIFI_PAIRING"
        const val ACTION_SUBMIT_CODE = "com.nfckeywallet.app.action.SUBMIT_WIFI_CODE"
        const val ACTION_STOP_PAIRING = "com.nfckeywallet.app.action.STOP_WIFI_PAIRING"

        const val ACTION_PAIRING_STATUS = "com.nfckeywallet.app.action.WIFI_PAIRING_STATUS"
        const val EXTRA_GRANTED = "granted"
        const val EXTRA_MESSAGE = "message"
        const val KEY_PAIRING_CODE = "wifi_pairing_code"

        fun startPairing(context: Context) {
            val intent = Intent(context, WirelessAdbPairingService::class.java).apply {
                action = ACTION_START_PAIRING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
