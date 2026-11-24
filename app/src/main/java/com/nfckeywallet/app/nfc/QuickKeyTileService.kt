package com.nfckeywallet.app.nfc

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nfckeywallet.app.QuickKeyPopupActivity
import com.nfckeywallet.app.data.KeyRepository

/**
 * Quick Settings Tile ("NFC Ключ") in the Android notification shade.
 * Tapping it collapses the shade and launches a 10-second floating popup window
 * that activates the selected NFC key and automatically restores the payment app afterward.
 */
class QuickKeyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val repo = KeyRepository(applicationContext)
        val selected = repo.getSelectedKey()
        tile.label = selected?.name?.take(18) ?: "NFC Ключ"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (selected != null) "10с ключ" else "Нет ключей"
        }
        tile.state = if (selected != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickKeyPopupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                QS_TILE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private const val QS_TILE_REQUEST_CODE = 4202
    }
}
