package com.nfckeywallet.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nfckeywallet.app.data.KeyRepository
import com.nfckeywallet.app.model.AppSettings
import com.nfckeywallet.app.model.NfcKey
import com.nfckeywallet.app.nfc.DefaultPaymentHelper
import com.nfckeywallet.app.nfc.InstalledPaymentApp
import com.nfckeywallet.app.nfc.KeyHceService
import com.nfckeywallet.app.nfc.NfcTagScanner
import com.nfckeywallet.app.nfc.PermissionGrantManager
import com.nfckeywallet.app.nfc.ScannedTagResult
import com.nfckeywallet.app.nfc.UidBackendType
import com.nfckeywallet.app.nfc.UidDiagnosticsReport
import com.nfckeywallet.app.nfc.UidEmulationManager
import com.nfckeywallet.app.nfc.UidEmulationStatus
import com.nfckeywallet.app.nfc.WirelessAdbHelper
import com.nfckeywallet.app.nfc.WirelessAdbPairingService
import com.nfckeywallet.app.ui.AddEditKeySheet
import com.nfckeywallet.app.ui.MainScreen
import com.nfckeywallet.app.ui.PermissionSetupSheet
import com.nfckeywallet.app.ui.SettingsSheet
import com.nfckeywallet.app.ui.theme.NfcKeyWalletTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val walletApp: NfcWalletApp get() = application as NfcWalletApp
    private lateinit var repository: KeyRepository
    private lateinit var paymentHelper: DefaultPaymentHelper
    private lateinit var uidEmulationManager: UidEmulationManager
    private var nfcAdapter: NfcAdapter? = null

    private var keysState by mutableStateOf<List<NfcKey>>(emptyList())
    private var selectedKeyIdState by mutableStateOf<String?>(null)
    private var settingsState by mutableStateOf(AppSettings())
    private var isNfcEnabledState by mutableStateOf(true)
    private var isPriorityActiveState by mutableStateOf(false)
    private var isSystemDefaultSwitchedState by mutableStateOf(false)
    private var hasWriteSecureSettingsState by mutableStateOf(false)
    private var isSystemDefaultWalletState by mutableStateOf(false)
    private var installedPaymentAppsState by mutableStateOf<List<InstalledPaymentApp>>(emptyList())
    private var preferredReturnComponentState by mutableStateOf<String?>(null)
    private var autoDetectedPreviousLabelState by mutableStateOf("Не определено")
    private var currentSystemLabelState by mutableStateOf("Не определено")
    private var uidStatusState by mutableStateOf(UidEmulationStatus())
    private var diagnosticsReportState by mutableStateOf<UidDiagnosticsReport?>(null)
    private var backupMessageState by mutableStateOf<String?>(null)
    private var remainingSecondsState by mutableIntStateOf(0)
    private var timerTrigger by mutableIntStateOf(0)
    private var lastTapMessageState by mutableStateOf<String?>(null)

    private var showAddEditSheet by mutableStateOf(false)
    private var editingKeyState by mutableStateOf<NfcKey?>(null)
    private var scannedTagState by mutableStateOf<ScannedTagResult?>(null)
    private var showSettingsSheet by mutableStateOf(false)
    private var showPermissionSetupSheet by mutableStateOf(false)
    private var permissionSetupBusy by mutableStateOf(false)
    private var permissionSetupStatus by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        launchWifiPairingServiceAndSettings()
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PermissionGrantManager.SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                runShizukuGrant()
            } else {
                permissionSetupStatus = "Доступ в Shizuku отклонён пользователем."
            }
        }
    }

    private val defaultWalletLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshSystemState()
    }

    private val exportKeysLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(repository.exportKeysJson().toByteArray(Charsets.UTF_8))
            }
            backupMessageState = "Экспортировано ключей: ${keysState.size}"
        }.onFailure {
            backupMessageState = "Ошибка экспорта: ${it.message}"
        }
    }

    private val importKeysLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            val added = repository.importKeysJson(json)
            keysState = repository.getKeys()
            selectedKeyIdState = repository.getSelectedKeyId() ?: keysState.firstOrNull()?.id
            syncAids()
            backupMessageState = if (added > 0) {
                "Импортировано новых ключей: $added"
            } else {
                "Новых ключей не найдено (возможно, уже добавлены)"
            }
        }.onFailure {
            backupMessageState = "Ошибка импорта: ${it.message}"
        }
    }

    private val tapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != KeyHceService.ACTION_NFC_TAP_EVENT) return
            val keyName = intent.getStringExtra(KeyHceService.EXTRA_KEY_NAME) ?: "Ключ"
            lastTapMessageState = "Считыватель сработал: «$keyName»"
            if (settingsState.vibrateOnTap) {
                triggerSuccessVibration()
            }
            if (settingsState.releaseAfterTap && isPriorityActiveState) {
                val shortened = minOf(if (remainingSecondsState > 0) remainingSecondsState else 3, 3)
                remainingSecondsState = shortened
                if (isSystemDefaultSwitchedState) {
                    walletApp.scheduleRestoreAfterSeconds(shortened)
                }
            }
        }
    }

    private val wifiPairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != WirelessAdbPairingService.ACTION_PAIRING_STATUS) return
            val granted = intent.getBooleanExtra(WirelessAdbPairingService.EXTRA_GRANTED, false)
            val msg = intent.getStringExtra(WirelessAdbPairingService.EXTRA_MESSAGE)
            permissionSetupBusy = false
            if (!msg.isNullOrBlank()) {
                permissionSetupStatus = msg
            }
            refreshSystemState()
            if (granted && keysState.isNotEmpty()) {
                activatePriority()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = walletApp.repository
        paymentHelper = walletApp.paymentHelper
        uidEmulationManager = walletApp.uidEmulationManager
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }

        keysState = repository.getKeys()
        selectedKeyIdState = repository.getSelectedKeyId() ?: keysState.firstOrNull()?.id
        settingsState = repository.getSettings()
        preferredReturnComponentState = repository.getPreferredReturnComponent()
        installedPaymentAppsState = paymentHelper.listInstalledPaymentApps()
        uidStatusState = uidEmulationManager.currentStatus
        syncAids()

        setContent {
            NfcKeyWalletTheme {
                // UI countdown synced with real deadline
                LaunchedEffect(isPriorityActiveState, timerTrigger, settingsState.autoReleaseSeconds) {
                    if (isPriorityActiveState && settingsState.autoReleaseSeconds > 0) {
                        while (isPriorityActiveState) {
                            val deadline = repository.getRestoreDeadlineMs()
                            if (deadline > 0L) {
                                val left = ((deadline - System.currentTimeMillis() + 999L) / 1000L).toInt()
                                if (left <= 0) {
                                    deactivatePriority()
                                    break
                                } else {
                                    remainingSecondsState = left
                                }
                            } else {
                                if (remainingSecondsState <= 0) {
                                    deactivatePriority()
                                    break
                                }
                                remainingSecondsState -= 1
                            }
                            delay(500L)
                        }
                    }
                }

                LaunchedEffect(lastTapMessageState) {
                    if (lastTapMessageState != null) {
                        delay(5000L)
                        lastTapMessageState = null
                    }
                }

                LaunchedEffect(showAddEditSheet) {
                    if (showAddEditSheet) {
                        enableTagScanningMode()
                    } else {
                        disableTagScanningMode()
                        if (isPriorityActiveState) {
                            paymentHelper.activateForegroundPriority(this@MainActivity)
                            applyHardwareUidForSelectedKey()
                        }
                    }
                }

                val selectedKey = keysState.firstOrNull { it.id == selectedKeyIdState }
                    ?: keysState.firstOrNull()

                MainScreen(
                    keys = keysState,
                    selectedKey = selectedKey,
                    isNfcEnabled = isNfcEnabledState,
                    isPriorityActive = isPriorityActiveState,
                    isSystemDefaultSwitched = isSystemDefaultSwitchedState,
                    hasWriteSecureSettings = hasWriteSecureSettingsState,
                    uidStatus = uidStatusState,
                    returnWalletLabel = autoDetectedPreviousLabelState,
                    remainingSeconds = remainingSecondsState,
                    settings = settingsState,
                    lastTapMessage = lastTapMessageState,
                    onSelectKey = { key ->
                        selectedKeyIdState = key.id
                        repository.setSelectedKeyId(key.id)
                        activatePriority()
                    },
                    onEditKey = { key ->
                        editingKeyState = key
                        scannedTagState = null
                        showAddEditSheet = true
                    },
                    onAddKeyClick = {
                        editingKeyState = null
                        scannedTagState = null
                        showAddEditSheet = true
                    },
                    onSettingsClick = {
                        backupMessageState = null
                        refreshSystemState()
                        showSettingsSheet = true
                    },
                    onOpenPermissionSetup = {
                        permissionSetupStatus = null
                        showPermissionSetupSheet = true
                    },
                    onActivatePriority = { activatePriority() },
                    onReleasePriority = { deactivatePriority() },
                    onExtendTimer = {
                        if (settingsState.autoReleaseSeconds > 0) {
                            remainingSecondsState = settingsState.autoReleaseSeconds
                            walletApp.scheduleRestoreAfterSeconds(settingsState.autoReleaseSeconds)
                            timerTrigger += 1
                        }
                    },
                    onOpenNfcSettings = {
                        runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                    }
                )

                if (showAddEditSheet) {
                    AddEditKeySheet(
                        editingKey = editingKeyState,
                        scannedTag = scannedTagState,
                        onDismiss = { showAddEditSheet = false },
                        onSave = { savedKey ->
                            keysState = repository.addOrUpdateKey(savedKey)
                            selectedKeyIdState = savedKey.id
                            repository.setSelectedKeyId(savedKey.id)
                            syncAids()
                            showAddEditSheet = false
                            activatePriority()
                        },
                        onDelete = { id ->
                            keysState = repository.deleteKey(id)
                            selectedKeyIdState = repository.getSelectedKeyId()
                            syncAids()
                            showAddEditSheet = false
                        }
                    )
                }

                if (showSettingsSheet) {
                    SettingsSheet(
                        settings = settingsState,
                        hasWriteSecureSettings = hasWriteSecureSettingsState,
                        isSystemDefaultWallet = isSystemDefaultWalletState,
                        installedPaymentApps = installedPaymentAppsState,
                        preferredReturnComponent = preferredReturnComponentState,
                        autoDetectedPreviousLabel = autoDetectedPreviousLabelState,
                        currentSystemLabel = currentSystemLabelState,
                        diagnosticsReport = diagnosticsReportState,
                        backupMessage = backupMessageState,
                        onOpenPermissionSetup = {
                            permissionSetupStatus = null
                            showSettingsSheet = false
                            showPermissionSetupSheet = true
                        },
                        onRunDiagnostics = {
                            refreshSystemState()
                            lifecycleScope.launch(Dispatchers.IO) {
                                val report = uidEmulationManager.runDiagnostics(hasWriteSecureSettingsState)
                                withContext(Dispatchers.Main) {
                                    diagnosticsReportState = report
                                }
                            }
                        },
                        onExportKeys = {
                            exportKeysLauncher.launch("nfc_keys_backup.json")
                        },
                        onImportKeys = {
                            importKeysLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        onSelectReturnComponent = { component ->
                            preferredReturnComponentState = component
                            repository.setPreferredReturnComponent(component)
                            refreshSystemState()
                        },
                        onUpdateSettings = { updated ->
                            settingsState = updated
                            repository.saveSettings(updated)
                            if (isPriorityActiveState) {
                                applyHardwareUidForSelectedKey()
                                if (updated.autoReleaseSeconds > 0) {
                                    remainingSecondsState = updated.autoReleaseSeconds
                                    walletApp.scheduleRestoreAfterSeconds(updated.autoReleaseSeconds)
                                    timerTrigger += 1
                                }
                            }
                        },
                        onRequestSystemDefaultWallet = {
                            val intent = paymentHelper.buildSystemDefaultPaymentIntent()
                            if (intent != null) {
                                runCatching { defaultWalletLauncher.launch(intent) }
                            }
                        },
                        onRecheckPermission = {
                            refreshSystemState()
                            if (hasWriteSecureSettingsState && keysState.isNotEmpty()) {
                                activatePriority()
                            }
                        },
                        onDismiss = { showSettingsSheet = false }
                    )
                }

                if (showPermissionSetupSheet) {
                    PermissionSetupSheet(
                        hasWriteSecureSettings = hasWriteSecureSettingsState,
                        isBusy = permissionSetupBusy,
                        statusMessage = permissionSetupStatus,
                        onAutoSetup = { runSmartAutoSetup() },
                        onStartWifiNotificationWizard = { startWifiNotificationWizard() },
                        onGrantViaWifiManual = { pairingPort, pairingCode, connectPort ->
                            permissionSetupBusy = true
                            permissionSetupStatus = "Подключаюсь к локальному Wireless ADB..."
                            lifecycleScope.launch(Dispatchers.IO) {
                                val (ok, msg) = WirelessAdbHelper.pairAndGrantPermission(
                                    context = this@MainActivity,
                                    pairingPortInput = pairingPort,
                                    pairingCode = pairingCode,
                                    connectPortInput = connectPort
                                )
                                withContext(Dispatchers.Main) {
                                    permissionSetupBusy = false
                                    permissionSetupStatus = msg
                                    refreshSystemState()
                                    if (ok && keysState.isNotEmpty()) {
                                        activatePriority()
                                    }
                                }
                            }
                        },
                        onGrantViaShizuku = { runShizukuGrant() },
                        onGrantViaRoot = { runRootGrant() },
                        onDismiss = { showPermissionSetupSheet = false }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
    }

    private fun runSmartAutoSetup() {
        when {
            PermissionGrantManager.isShizukuAvailable() -> runShizukuGrant()
            PermissionGrantManager.isRootLikelyAvailable() -> runRootGrant()
            else -> startWifiNotificationWizard()
        }
    }

    private fun startWifiNotificationWizard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        launchWifiPairingServiceAndSettings()
    }

    private fun launchWifiPairingServiceAndSettings() {
        WirelessAdbPairingService.startPairing(this)
        permissionSetupStatus = "Поиск Wi-Fi запущен! Включите «Отладка по Wi-Fi» → «Подключить по коду» и введите 6 цифр в уведомлении сверху."
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun runShizukuGrant() {
        permissionSetupBusy = true
        permissionSetupStatus = "Подключаюсь к Shizuku..."
        lifecycleScope.launch(Dispatchers.IO) {
            val (ok, msg) = PermissionGrantManager.grantViaShizuku(this@MainActivity)
            withContext(Dispatchers.Main) {
                permissionSetupBusy = false
                permissionSetupStatus = msg
                refreshSystemState()
                if (ok && keysState.isNotEmpty()) {
                    activatePriority()
                }
            }
        }
    }

    private fun runRootGrant() {
        permissionSetupBusy = true
        permissionSetupStatus = "Запрашиваю Root (su)..."
        lifecycleScope.launch(Dispatchers.IO) {
            val (ok, msg) = PermissionGrantManager.grantViaRoot(this@MainActivity)
            withContext(Dispatchers.Main) {
                permissionSetupBusy = false
                permissionSetupStatus = msg
                refreshSystemState()
                if (ok && keysState.isNotEmpty()) {
                    activatePriority()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSystemState()

        ContextCompat.registerReceiver(
            this,
            tapReceiver,
            IntentFilter(KeyHceService.ACTION_NFC_TAP_EVENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            wifiPairingReceiver,
            IntentFilter(WirelessAdbPairingService.ACTION_PAIRING_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val existingDeadline = repository.getRestoreDeadlineMs()
        if (existingDeadline > System.currentTimeMillis() && isSystemDefaultWalletState) {
            isPriorityActiveState = true
            isSystemDefaultSwitchedState = true
            remainingSecondsState = ((existingDeadline - System.currentTimeMillis() + 999L) / 1000L).toInt()
            paymentHelper.activateForegroundPriority(this)
            timerTrigger += 1
        } else if (existingDeadline in 1..System.currentTimeMillis()) {
            deactivatePriority()
        } else if (settingsState.autoActivateOnOpen && keysState.isNotEmpty() && !showAddEditSheet) {
            activatePriority()
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(tapReceiver) }
        runCatching { unregisterReceiver(wifiPairingReceiver) }
        disableTagScanningMode()
        paymentHelper.releaseForegroundPriority(this)

        if (!isSystemDefaultSwitchedState) {
            lifecycleScope.launch(Dispatchers.IO) {
                uidEmulationManager.stopUidEmulation(this@MainActivity)
                withContext(Dispatchers.Main) {
                    uidStatusState = uidEmulationManager.currentStatus
                }
            }
            isPriorityActiveState = false
            remainingSecondsState = 0
        }
    }

    private fun refreshSystemState() {
        isNfcEnabledState = paymentHelper.isNfcEnabled()
        hasWriteSecureSettingsState = paymentHelper.hasWriteSecureSettingsPermission()
        isSystemDefaultWalletState = paymentHelper.isSystemDefaultPaymentApp()
        installedPaymentAppsState = paymentHelper.listInstalledPaymentApps()
        preferredReturnComponentState = repository.getPreferredReturnComponent()
        uidStatusState = uidEmulationManager.currentStatus

        val currentComp = paymentHelper.getCurrentSystemPaymentComponent()
        currentSystemLabelState = paymentHelper.labelForComponent(currentComp)

        val returnComp = preferredReturnComponentState ?: repository.getPreviousPaymentComponent()
        autoDetectedPreviousLabelState = paymentHelper.labelForComponent(returnComp)

        if (isSystemDefaultSwitchedState && !isSystemDefaultWalletState) {
            isSystemDefaultSwitchedState = false
        }
    }

    private fun applyHardwareUidForSelectedKey() {
        val selectedKey = keysState.firstOrNull { it.id == selectedKeyIdState }
            ?: keysState.firstOrNull()
        val effectiveUid = selectedKey?.effectiveUidHex().orEmpty()
        val atqa = selectedKey?.safeAtqaHex() ?: "0400"
        val sak = selectedKey?.safeSakHex() ?: "28"
        val enableHw = settingsState.enableHardwareUidEmulation
        val backend = UidBackendType.fromId(settingsState.uidBackendPreference)
        val hasPerm = hasWriteSecureSettingsState

        lifecycleScope.launch(Dispatchers.IO) {
            if (!enableHw || effectiveUid.isBlank()) {
                uidEmulationManager.stopUidEmulation(this@MainActivity)
                withContext(Dispatchers.Main) {
                    uidStatusState = uidEmulationManager.currentStatus
                }
                return@launch
            }
            val status = uidEmulationManager.startUidEmulation(
                activity = this@MainActivity,
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

    private fun activatePriority() {
        if (keysState.isEmpty()) return
        repository.setEmulationEnabled(true)
        hasWriteSecureSettingsState = paymentHelper.hasWriteSecureSettingsPermission()

        val switchedInSystem = paymentHelper.switchSystemDefaultToSelf(repository)
        paymentHelper.activateForegroundPriority(this)
        applyHardwareUidForSelectedKey()

        isSystemDefaultSwitchedState = switchedInSystem
        isPriorityActiveState = true
        remainingSecondsState = settingsState.autoReleaseSeconds

        if (settingsState.autoReleaseSeconds > 0) {
            walletApp.scheduleRestoreAfterSeconds(settingsState.autoReleaseSeconds)
        } else {
            walletApp.cancelScheduledRestore()
            repository.setRestoreDeadlineMs(0L)
        }
        refreshSystemState()
        timerTrigger += 1
    }

    private fun deactivatePriority() {
        paymentHelper.releaseForegroundPriority(this)
        lifecycleScope.launch(Dispatchers.IO) {
            uidEmulationManager.stopUidEmulation(this@MainActivity)
            withContext(Dispatchers.Main) {
                walletApp.restoreNow()
                isPriorityActiveState = false
                isSystemDefaultSwitchedState = false
                remainingSecondsState = 0
                refreshSystemState()
            }
        }
    }

    private fun syncAids() {
        val customAids = keysState.map { it.customAidHex }
        paymentHelper.syncCustomAids(customAids)
    }

    private fun enableTagScanningMode() {
        val adapter = nfcAdapter ?: return
        paymentHelper.releaseForegroundPriority(this)
        uidEmulationManager.ensureDiscoveryReset(this)
        lifecycleScope.launch(Dispatchers.IO) {
            uidEmulationManager.stopUidEmulation(this@MainActivity)
            withContext(Dispatchers.Main) {
                uidStatusState = uidEmulationManager.currentStatus
            }
        }

        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
        runCatching {
            adapter.enableReaderMode(this, { tag ->
                val result = NfcTagScanner.parseTag(tag)
                runOnUiThread {
                    scannedTagState = result
                    if (settingsState.vibrateOnTap) {
                        triggerSuccessVibration()
                    }
                }
            }, flags, null)
        }
    }

    private fun disableTagScanningMode() {
        runCatching { nfcAdapter?.disableReaderMode(this) }
    }

    private fun triggerSuccessVibration() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
