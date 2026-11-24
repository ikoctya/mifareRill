package com.nfckeywallet.app.nfc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nfckeywallet.app.NfcWalletApp

/**
 * Fires via AlarmManager even when Xiaomi HyperOS / MIUI freezes the app in the background,
 * guaranteeing that the previous default payment app is restored when the timer expires.
 */
class RestorePaymentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? NfcWalletApp
        if (app != null) {
            app.restoreNow()
        } else {
            val repo = com.nfckeywallet.app.data.KeyRepository(context)
            val helper = DefaultPaymentHelper(context)
            repo.setRestoreDeadlineMs(0L)
            repo.setEmulationEnabled(false)
            helper.restorePreviousSystemDefault(repo)
        }
    }
}
