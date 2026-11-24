package com.nfckeywallet.app

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nfckeywallet.app.data.KeyRepository
import com.nfckeywallet.app.nfc.DefaultPaymentHelper
import com.nfckeywallet.app.nfc.RestorePaymentReceiver
import com.nfckeywallet.app.nfc.UidEmulationManager

/**
 * Application-scoped manager for the auto-restore timer and hardware UID emulation lifecycle.
 * Uses BOTH Handler.postDelayed (instant while process is awake) AND AlarmManager
 * (wakes RestorePaymentReceiver even if HyperOS/MIUI freezes the app in background).
 */
class NfcWalletApp : Application() {

    lateinit var repository: KeyRepository
        private set
    lateinit var paymentHelper: DefaultPaymentHelper
        private set
    lateinit var uidEmulationManager: UidEmulationManager
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var restoreRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        repository = KeyRepository(this)
        paymentHelper = DefaultPaymentHelper(this)
        uidEmulationManager = UidEmulationManager(this)

        val deadline = repository.getRestoreDeadlineMs()
        if (deadline > 0L) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) {
                restoreNow()
            } else {
                scheduleRestoreAfterMs(remaining)
            }
        }
    }

    fun scheduleRestoreAfterSeconds(seconds: Int) {
        cancelScheduledRestore()
        if (seconds <= 0) {
            repository.setRestoreDeadlineMs(0L)
            return
        }
        val delayMs = seconds * 1000L
        repository.setRestoreDeadlineMs(System.currentTimeMillis() + delayMs)
        scheduleRestoreAfterMs(delayMs)
    }

    private fun scheduleRestoreAfterMs(delayMs: Long) {
        cancelScheduledRestore()

        val runnable = Runnable {
            restoreNow()
        }
        restoreRunnable = runnable
        handler.postDelayed(runnable, delayMs)

        runCatching {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val triggerAt = SystemClock.elapsedRealtime() + delayMs
            alarmManager?.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                buildRestorePendingIntent()
            )
        }
    }

    fun cancelScheduledRestore() {
        restoreRunnable?.let { handler.removeCallbacks(it) }
        restoreRunnable = null
        runCatching {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(buildRestorePendingIntent())
        }
    }

    fun restoreNow(): Boolean {
        cancelScheduledRestore()
        repository.setRestoreDeadlineMs(0L)
        repository.setEmulationEnabled(false)
        uidEmulationManager.stopUidEmulation()
        return paymentHelper.restorePreviousSystemDefault(repository)
    }

    private fun buildRestorePendingIntent(): PendingIntent {
        val intent = Intent(this, RestorePaymentReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            RESTORE_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val RESTORE_ALARM_REQUEST_CODE = 4201
    }
}
