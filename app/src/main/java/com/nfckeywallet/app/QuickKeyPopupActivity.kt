package com.nfckeywallet.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nfckeywallet.app.data.KeyRepository
import com.nfckeywallet.app.model.NfcKey
import com.nfckeywallet.app.nfc.DefaultPaymentHelper
import com.nfckeywallet.app.nfc.KeyHceService
import com.nfckeywallet.app.nfc.UidBackendType
import com.nfckeywallet.app.nfc.UidEmulationManager
import com.nfckeywallet.app.nfc.UidEmulationStatus
import com.nfckeywallet.app.ui.components.KeyPresets
import com.nfckeywallet.app.ui.theme.NfcKeyWalletTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Floating 10-second popup overlay launched from the Quick Settings Tile.
 * Activates the selected NFC key (system wallet + hardware UID Raw NCI), counts down 10 seconds,
 * then automatically restores the previous payment app and closes itself.
 */
class QuickKeyPopupActivity : ComponentActivity() {

    private val walletApp: NfcWalletApp get() = application as NfcWalletApp
    private lateinit var repository: KeyRepository
    private lateinit var paymentHelper: DefaultPaymentHelper
    private lateinit var uidEmulationManager: UidEmulationManager

    private var keysState by mutableStateOf<List<NfcKey>>(emptyList())
    private var selectedKeyIdState by mutableStateOf<String?>(null)
    private var uidStatusState by mutableStateOf(UidEmulationStatus())
    private var remainingSecondsState by mutableIntStateOf(POPUP_DURATION_SECONDS)
    private var timerTrigger by mutableIntStateOf(0)
    private var tapDetectedText by mutableStateOf<String?>(null)

    private val restoredOnce = AtomicBoolean(false)

    private val tapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != KeyHceService.ACTION_NFC_TAP_EVENT) return
            val keyName = intent.getStringExtra(KeyHceService.EXTRA_KEY_NAME) ?: "Ключ"
            tapDetectedText = "Сработало: «$keyName»"
            triggerVibration()
            remainingSecondsState = minOf(remainingSecondsState, 2)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = walletApp.repository
        paymentHelper = walletApp.paymentHelper
        uidEmulationManager = walletApp.uidEmulationManager

        keysState = repository.getKeys()
        selectedKeyIdState = repository.getSelectedKeyId() ?: keysState.firstOrNull()?.id

        if (keysState.isEmpty()) {
            // No keys yet — open main screen instead
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            NfcKeyWalletTheme(darkTheme = true) {
                LaunchedEffect(timerTrigger) {
                    while (remainingSecondsState > 0) {
                        delay(1000L)
                        remainingSecondsState -= 1
                    }
                    restoreAndClose()
                }

                val activeKey = keysState.firstOrNull { it.id == selectedKeyIdState }
                    ?: keysState.first()

                QuickKeyPopupContent(
                    keys = keysState,
                    activeKey = activeKey,
                    uidStatus = uidStatusState,
                    remainingSeconds = remainingSecondsState,
                    totalSeconds = POPUP_DURATION_SECONDS,
                    tapDetectedText = tapDetectedText,
                    onSelectKey = { key ->
                        selectedKeyIdState = key.id
                        repository.setSelectedKeyId(key.id)
                        remainingSecondsState = POPUP_DURATION_SECONDS
                        timerTrigger += 1
                        activateSelectedKey(key)
                    },
                    onExtend = {
                        remainingSecondsState = POPUP_DURATION_SECONDS
                        walletApp.scheduleRestoreAfterSeconds(POPUP_DURATION_SECONDS)
                        timerTrigger += 1
                    },
                    onDismiss = { restoreAndClose() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        restoredOnce.set(false)
        ContextCompat.registerReceiver(
            this,
            tapReceiver,
            IntentFilter(KeyHceService.ACTION_NFC_TAP_EVENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val activeKey = keysState.firstOrNull { it.id == selectedKeyIdState }
            ?: keysState.firstOrNull()
            ?: return
        activateSelectedKey(activeKey)
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(tapReceiver) }
        if (!isFinishing) {
            restoreAndClose()
        }
    }

    private fun activateSelectedKey(key: NfcKey) {
        repository.setEmulationEnabled(true)
        paymentHelper.switchSystemDefaultToSelf(repository)
        paymentHelper.activateForegroundPriority(this)
        walletApp.scheduleRestoreAfterSeconds(POPUP_DURATION_SECONDS)

        val settings = repository.getSettings()
        val hasPerm = paymentHelper.hasWriteSecureSettingsPermission()
        val effectiveUid = key.effectiveUidHex()
        val atqa = key.safeAtqaHex()
        val sak = key.safeSakHex()
        val backend = UidBackendType.fromId(settings.uidBackendPreference)

        if (settings.enableHardwareUidEmulation && effectiveUid.isNotBlank()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val status = uidEmulationManager.startUidEmulation(
                    activity = this@QuickKeyPopupActivity,
                    uidHex = effectiveUid,
                    atqaHex = atqa,
                    sakHex = sak,
                    preferredBackend = backend,
                    hasWriteSecureSettings = hasPerm
                )
                withContext(Dispatchers.Main) {
                    uidStatusState = status
                }
            }
        }
    }

    private fun restoreAndClose() {
        if (!restoredOnce.compareAndSet(false, true)) {
            if (!isFinishing) finish()
            return
        }
        paymentHelper.releaseForegroundPriority(this)
        lifecycleScope.launch(Dispatchers.IO) {
            uidEmulationManager.stopUidEmulation(this@QuickKeyPopupActivity)
            withContext(Dispatchers.Main) {
                walletApp.restoreNow()
                if (!isFinishing) finish()
            }
        }
    }

    private fun triggerVibration() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    companion object {
        const val POPUP_DURATION_SECONDS = 10
    }
}

@Composable
private fun QuickKeyPopupContent(
    keys: List<NfcKey>,
    activeKey: NfcKey,
    uidStatus: UidEmulationStatus,
    remainingSeconds: Int,
    totalSeconds: Int,
    tapDetectedText: String?,
    onSelectKey: (NfcKey) -> Unit,
    onExtend: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorPreset = KeyPresets.colorFor(activeKey.colorId)
    val infiniteTransition = rememberInfiniteTransition(label = "popupPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF121726),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header with timer badge and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Подносите к считывателю • ${remainingSeconds}с",
                        color = Color(0xFF5CE1B8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Закрыть",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF5CE1B8),
                    trackColor = Color.White.copy(alpha = 0.12f)
                )

                Spacer(Modifier.height(14.dp))

                // Active key gradient card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colorPreset.brush())
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = KeyPresets.iconFor(activeKey.iconId),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeKey.name,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val uid = activeKey.effectiveUidHex()
                            if (uid.isNotBlank()) {
                                Text(
                                    text = if (activeKey.reverseOctets) "UID $uid ⇄" else "UID $uid",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .scale(pulseScale)
                                    .alpha(pulseAlpha)
                                    .clip(CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Contactless,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Hardware UID / Tap confirmation badge
                Spacer(Modifier.height(10.dp))
                val statusText = tapDetectedText ?: uidStatus.detailMessage
                val isOk = tapDetectedText != null || uidStatus.active
                Surface(
                    color = if (isOk) Color(0xFF10B981).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isOk) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (isOk) Color(0xFF5CE1B8) else Color(0xFFFFB77C),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Quick key switcher if user has multiple keys
                if (keys.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 4.dp)
                    ) {
                        items(keys, key = { it.id }) { keyItem ->
                            FilterChip(
                                selected = keyItem.id == activeKey.id,
                                onClick = { onSelectKey(keyItem) },
                                label = {
                                    Text(
                                        text = keyItem.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onExtend,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+10с")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1D2438),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Вернуть оплату")
                    }
                }
            }
        }
    }
}
