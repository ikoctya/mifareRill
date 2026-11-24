package com.nfckeywallet.app.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class UidBackendType(val id: String, val title: String) {
    AUTO("AUTO", "Авто"),
    OEM_EXTENSION("OEM_EXTENSION", "Android 15 OEM"),
    RAW_NCI("RAW_NCI", "Raw NCI"),
    ROOT_SU("ROOT_SU", "Root (su)"),
    HCE_ONLY("HCE_ONLY", "Только HCE");

    companion object {
        fun fromId(id: String?): UidBackendType =
            entries.firstOrNull { it.id == id } ?: AUTO
    }
}

data class UidEmulationStatus(
    val active: Boolean = false,
    val backendUsed: UidBackendType = UidBackendType.HCE_ONLY,
    val activeUidHex: String = "",
    val detailMessage: String = "Аппаратный UID не активен (стандартный HCE)"
)

data class UidDiagnosticsReport(
    val deviceModel: String,
    val androidVersion: String,
    val apiLevel: Int,
    val hasWriteSecureSettings: Boolean,
    val hasOemExtensionClass: Boolean,
    val hasOemEmulateMethod: Boolean,
    val hasVendorNciMethod: Boolean,
    val hasVendorNciCallback: Boolean,
    val hasRootAccess: Boolean,
    val detectedOemMethods: List<String>,
    val lastNciLog: String,
    val summary: String
) {
    fun toCopyableText(): String = buildString {
        appendLine("=== NFC Ключница v2.1.0 Диагностика UID ===")
        appendLine("Устройство: $deviceModel")
        appendLine("Android: $androidVersion (API $apiLevel)")
        appendLine("WRITE_SECURE_SETTINGS: ${if (hasWriteSecureSettings) "ДА" else "НЕТ"}")
        appendLine("NfcOemExtension класс: ${if (hasOemExtensionClass) "НАЙДЕН" else "НЕТ"}")
        appendLine("emulateNfcTechnologyATag: ${if (hasOemEmulateMethod) "НАЙДЕН" else "НЕТ"}")
        appendLine("sendVendorNciMessage (Raw NCI): ${if (hasVendorNciMethod) "НАЙДЕН" else "НЕТ"}")
        appendLine("registerNfcVendorNciCallback: ${if (hasVendorNciCallback) "НАЙДЕН" else "НЕТ"}")
        appendLine("Root (su): ${if (hasRootAccess) "ДОСТУПЕН" else "НЕТ"}")
        if (detectedOemMethods.isNotEmpty()) {
            appendLine("Методы OEM/NCI: ${detectedOemMethods.joinToString(", ")}")
        }
        if (lastNciLog.isNotBlank()) {
            appendLine("Последний ответ NCI: $lastNciLog")
        }
        appendLine("Итог: $summary")
    }
}

/**
 * Hardware ISO 14443-A UID (NFCID1) + ATQA + SAK emulation coordinator (v1.6.2).
 *
 * Raw NCI sequence:
 * 1. setDiscoveryTechOnUi(activity, 0, 0) -> enters RFST_IDLE cleanly.
 * 2. CORE_SET_CONFIG_CMD (GID=0x00, OID=0x02) with ATQA (0x30/0x31), SAK (0x32), UID (0x33).
 * 3. CORE_GET_CONFIG_CMD -> verify written registers.
 * 4. resetDiscoveryTechOnUi(activity) -> restores full Android NFC RF discovery (never leaves
 *    polling/antenna stuck) and verifies with GET_AFTER that UID survived restart.
 */
class UidEmulationManager(private val context: Context) {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context.applicationContext)
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    var currentStatus: UidEmulationStatus = UidEmulationStatus()
        private set

    @Volatile
    var lastNciLog: String = ""
        private set

    private var activeOemExtensionObj: Any? = null
    private var rootConfigBackupPath: String? = null
    private var lastActivityRef: Activity? = null
    private var discoveryOverridden: Boolean = false

    fun ensureDiscoveryReset(activity: Activity) {
        val adapter = nfcAdapter ?: return
        runCatching {
            val m = adapter.javaClass.methods.firstOrNull {
                it.name == "resetDiscoveryTechnology" && it.parameterCount == 1
            }
            m?.invoke(adapter, activity)
            discoveryOverridden = false
        }
    }

    fun startUidEmulation(
        activity: Activity?,
        uidHex: String,
        atqaHex: String,
        sakHex: String,
        preferredBackend: UidBackendType,
        hasWriteSecureSettings: Boolean
    ): UidEmulationStatus {
        lastActivityRef = activity
        val uidBytes = HexUtils.hexToBytes(uidHex)
        if (uidBytes.size !in listOf(4, 7, 10)) {
            currentStatus = UidEmulationStatus(
                active = false,
                backendUsed = UidBackendType.HCE_ONLY,
                activeUidHex = uidHex,
                detailMessage = "UID должен быть 4, 7 или 10 байт (сейчас ${uidBytes.size} Б) — работает HCE"
            )
            return currentStatus
        }

        val atqaBytes = HexUtils.hexToBytes(atqaHex).let {
            if (it.size == 2) it else byteArrayOf(0x04, 0x00)
        }
        val sakByte = HexUtils.hexToBytes(sakHex).firstOrNull() ?: 0x20.toByte()

        if (preferredBackend == UidBackendType.HCE_ONLY) {
            stopUidEmulation(activity)
            currentStatus = UidEmulationStatus(
                active = false,
                backendUsed = UidBackendType.HCE_ONLY,
                activeUidHex = uidHex,
                detailMessage = "Выбран режим «Только HCE»"
            )
            return currentStatus
        }

        val order = when (preferredBackend) {
            UidBackendType.AUTO -> listOf(
                UidBackendType.OEM_EXTENSION,
                UidBackendType.RAW_NCI,
                UidBackendType.ROOT_SU
            )
            else -> listOf(preferredBackend)
        }

        val errors = mutableListOf<String>()

        for (backend in order) {
            val result = when (backend) {
                UidBackendType.OEM_EXTENSION -> tryStartOemExtension(uidBytes, uidHex, hasWriteSecureSettings)
                UidBackendType.RAW_NCI -> tryStartRawNci(
                    activity = activity,
                    uidBytes = uidBytes,
                    uidHex = uidHex,
                    atqaBytes = atqaBytes,
                    sakByte = sakByte,
                    hasWriteSecureSettings = hasWriteSecureSettings
                )
                UidBackendType.ROOT_SU -> tryStartRootUid(uidBytes, uidHex)
                else -> null
            }
            if (result != null && result.first) {
                currentStatus = UidEmulationStatus(
                    active = true,
                    backendUsed = backend,
                    activeUidHex = uidHex,
                    detailMessage = result.second
                )
                return currentStatus
            } else if (result != null) {
                errors.add("${backend.title}: ${result.second}")
            }
        }

        currentStatus = UidEmulationStatus(
            active = false,
            backendUsed = UidBackendType.HCE_ONLY,
            activeUidHex = uidHex,
            detailMessage = errors.firstOrNull() ?: "Аппаратный UID не поддерживается прошивкой — работает HCE"
        )
        return currentStatus
    }

    fun stopUidEmulation(activity: Activity? = lastActivityRef) {
        val used = currentStatus.backendUsed
        when (used) {
            UidBackendType.OEM_EXTENSION -> stopOemExtension()
            UidBackendType.RAW_NCI -> stopRawNci(activity)
            UidBackendType.ROOT_SU -> stopRootUid()
            else -> {
                if (discoveryOverridden && activity != null) {
                    resetDiscoveryTechOnUi(activity)
                }
            }
        }
        currentStatus = UidEmulationStatus(
            active = false,
            backendUsed = UidBackendType.HCE_ONLY,
            activeUidHex = "",
            detailMessage = "Аппаратный UID остановлен"
        )
    }

    // -------------------------------------------------------------------------
    // Backend 1: Android 15+ NfcOemExtension
    // -------------------------------------------------------------------------
    private fun tryStartOemExtension(
        uidBytes: ByteArray,
        uidHex: String,
        hasWriteSecureSettings: Boolean
    ): Pair<Boolean, String> {
        if (Build.VERSION.SDK_INT < 35) {
            return false to "Нужен Android 15+ (у вас API ${Build.VERSION.SDK_INT})"
        }
        if (!hasWriteSecureSettings) {
            return false to "Нет права WRITE_SECURE_SETTINGS"
        }
        val adapter = nfcAdapter ?: return false to "NFC адаптер недоступен"

        return try {
            val getExtMethod = adapter.javaClass.methods.firstOrNull {
                it.name == "getNfcOemExtension" && it.parameterCount == 0
            } ?: return false to "Метод getNfcOemExtension отсутствует"

            val oemExt = getExtMethod.invoke(adapter)
                ?: return false to "getNfcOemExtension вернул null"
            activeOemExtensionObj = oemExt

            val emulateMethod = oemExt.javaClass.methods.firstOrNull { m ->
                (m.name.contains("emulateNfcTechnologyA", ignoreCase = true) ||
                    m.name.contains("setNfcId1", ignoreCase = true) ||
                    m.name.contains("emulateUid", ignoreCase = true)) &&
                    m.parameterTypes.any { it == ByteArray::class.java }
            } ?: return false to "Нет emulateNfcTechnologyATag в прошивке"

            val args = emulateMethod.parameterTypes.map { paramType ->
                when (paramType) {
                    ByteArray::class.java -> uidBytes
                    Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> true
                    Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
                    else -> null
                }
            }.toTypedArray()

            emulateMethod.invoke(oemExt, *args)
            true to "Аппаратный UID $uidHex активен (Android 15 OEM)"
        } catch (e: Exception) {
            val rootCause = e.cause ?: e
            false to "${rootCause.javaClass.simpleName}: ${rootCause.message ?: "отклонено"}"
        }
    }

    private fun stopOemExtension() {
        val oemExt = activeOemExtensionObj ?: return
        runCatching {
            val stopMethod = oemExt.javaClass.methods.firstOrNull { m ->
                (m.name.contains("stopEmulate", ignoreCase = true) ||
                    m.name.contains("clearEmulate", ignoreCase = true) ||
                    m.name.contains("resetNfcId1", ignoreCase = true)) &&
                    m.parameterCount == 0
            }
            stopMethod?.invoke(oemExt)
        }
        activeOemExtensionObj = null
    }

    // -------------------------------------------------------------------------
    // Backend 2: Android 15/16 Raw NCI
    // IDLE(0,0) -> CORE_SET_CONFIG -> resetDiscoveryTechnology -> GET_AFTER
    // -------------------------------------------------------------------------
    private fun tryStartRawNci(
        activity: Activity?,
        uidBytes: ByteArray,
        uidHex: String,
        atqaBytes: ByteArray,
        sakByte: Byte,
        hasWriteSecureSettings: Boolean
    ): Pair<Boolean, String> {
        if (Build.VERSION.SDK_INT < 35) {
            return false to "Нужен Android 15+ (у вас API ${Build.VERSION.SDK_INT})"
        }
        if (!hasWriteSecureSettings) {
            return false to "Нет права WRITE_SECURE_SETTINGS"
        }
        val adapter = nfcAdapter ?: return false to "NFC адаптер недоступен"
        val sendMethod = findSendVendorNciMethod(adapter)
            ?: return false to "sendVendorNciMessage недоступен"

        val logs = mutableListOf<String>()

        return try {
            // Step 1: Enter RFST_IDLE cleanly on UI thread
            if (activity != null) {
                val (idleOk, idleErr) = setDiscoveryTechOnUi(activity, 0, 0)
                logs.add(if (idleOk) "IDLE=true" else "IDLE=false($idleErr)")
                if (idleOk) {
                    discoveryOverridden = true
                    Thread.sleep(120L)
                }
            }

            // Step 2: Send CORE_SET_CONFIG_CMD with ATQA (0x30/0x31), SAK (0x32), UID (0x33)
            val fullPayload = buildCoreSetConfigPayload(
                listOf(
                    0x30 to byteArrayOf(atqaBytes[0]),
                    0x31 to byteArrayOf(atqaBytes[1]),
                    0x32 to byteArrayOf(sakByte),
                    0x33 to uidBytes
                )
            )
            var setResp = sendNciAndAwaitResponse(adapter, sendMethod, 0x01, 0x00, 0x02, fullPayload)
            logs.add("SET4=$setResp")

            val sakStr = String.format("%02X", sakByte.toInt() and 0xFF)
            var appliedMode = "UID+SAK($sakStr)"

            if (!isNciResponseOk(setResp)) {
                val sakUidPayload = buildCoreSetConfigPayload(
                    listOf(
                        0x32 to byteArrayOf(sakByte),
                        0x33 to uidBytes
                    )
                )
                setResp = sendNciAndAwaitResponse(adapter, sendMethod, 0x01, 0x00, 0x02, sakUidPayload)
                logs.add("SET2=$setResp")
            }

            if (!isNciResponseOk(setResp)) {
                val uidOnlyPayload = buildCoreSetConfigPayload(listOf(0x33 to uidBytes))
                setResp = sendNciAndAwaitResponse(adapter, sendMethod, 0x01, 0x00, 0x02, uidOnlyPayload)
                logs.add("SET1=$setResp")
                appliedMode = "UID"
            }

            // Step 3: Restore normal Android NFC discovery so antenna wakes up and ReaderMode never hangs
            if (activity != null) {
                Thread.sleep(40L)
                resetDiscoveryTechOnUi(activity)
                logs.add("RESET_DISC=true")
                Thread.sleep(150L)
            }

            // Step 4: Verify registers AFTER discovery restarted (confirms Android didn't overwrite UID)
            val getPayload = byteArrayOf(0x02, 0x32, 0x33)
            val getAfterResp = sendNciAndAwaitResponse(adapter, sendMethod, 0x01, 0x00, 0x03, getPayload)
            logs.add("GET_AFTER=$getAfterResp")

            lastNciLog = logs.joinToString(" | ")

            val expectedUidSpaced = HexUtils.bytesToHex(uidBytes, " ").uppercase()
            val chipConfirmedUid = getAfterResp.startsWith("OK(") &&
                getAfterResp.uppercase().contains(expectedUidSpaced)

            if (chipConfirmedUid) {
                true to "Активно в эфире: $appliedMode = $uidHex"
            } else if (isNciResponseOk(setResp)) {
                false to "Записано, но Android сбросил UID после перезапуска (см. Диагностику)"
            } else {
                false to "Чип отклонил Raw NCI: $setResp (см. Диагностику)"
            }
        } catch (e: Exception) {
            if (activity != null) {
                resetDiscoveryTechOnUi(activity)
            }
            val rootCause = e.cause ?: e
            lastNciLog = "${logs.joinToString(" | ")} | EXC=${rootCause.javaClass.simpleName}: ${rootCause.message}"
            false to "${rootCause.javaClass.simpleName}: ${rootCause.message ?: "заблокировано"}"
        }
    }

    private fun stopRawNci(activity: Activity?) {
        val adapter = nfcAdapter ?: return
        runCatching {
            val sendMethod = findSendVendorNciMethod(adapter)
            if (activity != null) {
                setDiscoveryTechOnUi(activity, 0, 0)
                Thread.sleep(100L)
            }
            if (sendMethod != null) {
                val resetPayload = buildCoreSetConfigPayload(
                    listOf(
                        0x30 to byteArrayOf(0x04),
                        0x31 to byteArrayOf(0x00),
                        0x32 to byteArrayOf(0x20),
                        0x33 to ByteArray(0)
                    )
                )
                sendNciAndAwaitResponse(adapter, sendMethod, 0x01, 0x00, 0x02, resetPayload)
            }
            if (activity != null) {
                resetDiscoveryTechOnUi(activity)
            }
        }
    }

    private fun buildCoreSetConfigPayload(params: List<Pair<Int, ByteArray>>): ByteArray {
        val totalSize = 1 + params.sumOf { 2 + it.second.size }
        val out = ByteArray(totalSize)
        var offset = 0
        out[offset++] = params.size.toByte()
        for ((paramId, value) in params) {
            out[offset++] = paramId.toByte()
            out[offset++] = value.size.toByte()
            if (value.isNotEmpty()) {
                System.arraycopy(value, 0, out, offset, value.size)
                offset += value.size
            }
        }
        return out
    }

    private fun isNciResponseOk(respSummary: String): Boolean {
        return respSummary.startsWith("OK") || respSummary.startsWith("QUEUED_NO_CB")
    }

    private fun sendNciAndAwaitResponse(
        adapter: NfcAdapter,
        sendMethod: Method,
        mt: Int,
        gid: Int,
        oid: Int,
        payload: ByteArray
    ): String {
        val callbackClass = runCatching {
            Class.forName("android.nfc.NfcAdapter\$NfcVendorNciCallback")
        }.getOrNull()
        val registerMethod = if (callbackClass != null) {
            adapter.javaClass.methods.firstOrNull {
                it.name == "registerNfcVendorNciCallback" && it.parameterCount == 2
            }
        } else null
        val unregisterMethod = if (callbackClass != null) {
            adapter.javaClass.methods.firstOrNull {
                it.name == "unregisterNfcVendorNciCallback" && it.parameterCount == 1
            }
        } else null

        if (callbackClass == null || registerMethod == null) {
            val code = sendMethod.invoke(adapter, mt, gid, oid, payload) as? Int ?: -1
            return if (code == 0) "QUEUED_NO_CB" else "SEND_ERR=$code"
        }

        val latch = CountDownLatch(1)
        val responseRef = AtomicReference<String?>(null)

        val proxy = Proxy.newProxyInstance(
            callbackClass.classLoader,
            arrayOf(callbackClass)
        ) { proxyObj, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxyObj)
                "equals" -> proxyObj === args?.firstOrNull()
                "toString" -> "NfcVendorNciCallbackProxy@${System.identityHashCode(proxyObj)}"
                "onVendorNciResponse" -> {
                    if (args != null && args.size >= 3) {
                        val rGid = (args[0] as? Int) ?: -1
                        val rOid = (args[1] as? Int) ?: -1
                        val rPayload = (args[2] as? ByteArray) ?: ByteArray(0)
                        if (rGid == gid && rOid == oid) {
                            val statusByte = rPayload.firstOrNull()?.toInt()?.and(0xFF) ?: -1
                            val hex = HexUtils.bytesToHex(rPayload, " ")
                            val label = if (statusByte == 0x00) {
                                "OK($hex)"
                            } else {
                                "NCI_ERR(0x${String.format("%02X", statusByte)}:$hex)"
                            }
                            responseRef.set(label)
                            latch.countDown()
                        }
                    }
                    null
                }
                else -> null
            }
        }

        return try {
            registerMethod.invoke(adapter, callbackExecutor, proxy)
            val sendCode = sendMethod.invoke(adapter, mt, gid, oid, payload) as? Int ?: -1
            if (sendCode != 0) {
                return "SEND_ERR=$sendCode"
            }
            val received = latch.await(500, TimeUnit.MILLISECONDS)
            if (received) {
                responseRef.get() ?: "OK(empty)"
            } else {
                "QUEUED_NO_CB(timeout)"
            }
        } catch (e: Exception) {
            val cause = e.cause ?: e
            "CB_ERR(${cause.javaClass.simpleName}:${cause.message ?: ""})"
        } finally {
            runCatching { unregisterMethod?.invoke(adapter, proxy) }
        }
    }

    private fun setDiscoveryTechOnUi(activity: Activity, pollTech: Int, listenTech: Int): Pair<Boolean, String> {
        val adapter = nfcAdapter ?: return false to "no_adapter"
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        val errRef = AtomicReference("")
        activity.runOnUiThread {
            try {
                val m = adapter.javaClass.methods.firstOrNull {
                    it.name == "setDiscoveryTechnology" && it.parameterCount == 3
                }
                if (m != null) {
                    m.invoke(adapter, activity, pollTech, listenTech)
                    ok.set(true)
                } else {
                    errRef.set("no_method")
                }
            } catch (e: Exception) {
                val cause = e.cause ?: e
                errRef.set("${cause.javaClass.simpleName}:${cause.message ?: ""}")
            } finally {
                latch.countDown()
            }
        }
        latch.await(400, TimeUnit.MILLISECONDS)
        return ok.get() to errRef.get()
    }

    private fun resetDiscoveryTechOnUi(activity: Activity) {
        val adapter = nfcAdapter ?: return
        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            try {
                val m = adapter.javaClass.methods.firstOrNull {
                    it.name == "resetDiscoveryTechnology" && it.parameterCount == 1
                }
                m?.invoke(adapter, activity)
                discoveryOverridden = false
            } catch (_: Exception) {
            } finally {
                latch.countDown()
            }
        }
        latch.await(400, TimeUnit.MILLISECONDS)
    }

    private fun findSendVendorNciMethod(adapter: NfcAdapter): Method? {
        return adapter.javaClass.methods.firstOrNull { m ->
            m.name == "sendVendorNciMessage" && m.parameterCount == 4
        }
    }

    // -------------------------------------------------------------------------
    // Backend 3: Root (su)
    // -------------------------------------------------------------------------
    private fun tryStartRootUid(uidBytes: ByteArray, uidHex: String): Pair<Boolean, String> {
        if (!isRootAvailable()) {
            return false to "Root-доступ (su) не найден"
        }
        val uidSpaced = HexUtils.bytesToHex(uidBytes, ", ")
        val configPaths = listOf(
            "/data/adb/modules/nfckeywallet/system/vendor/etc/libnfc-nci.conf",
            "/vendor/etc/libnfc-nci.conf",
            "/system/etc/libnfc-nci.conf"
        )
        val existingPath = configPaths.firstOrNull { path ->
            runSuCommand("test -f $path && echo OK").contains("OK")
        } ?: return false to "Конфиг libnfc-nci.conf не найден"

        val writable = runSuCommand("test -w $existingPath && echo W").contains("W")
        if (!writable) {
            return false to "$existingPath только для чтения (нужен Magisk bind-mount / LSPosed)"
        }

        val backupPath = "/data/local/tmp/nfckeywallet_nfc_backup.conf"
        runSuCommand("if [ ! -f $backupPath ]; then cp $existingPath $backupPath; fi")
        rootConfigBackupPath = backupPath

        val tlvLen = uidBytes.size + 3
        val cfgLine = "NFA_DM_START_UP_CFG={${String.format("%02X", tlvLen)}:CB:01:01:33:${String.format("%02X", uidBytes.size)}:${HexUtils.bytesToHex(uidBytes, ":")}}"
        val patchCmd = "grep -v '^NFA_DM_START_UP_CFG=' $backupPath > /data/local/tmp/nfc_new.conf && " +
            "echo '$cfgLine' >> /data/local/tmp/nfc_new.conf && " +
            "cp /data/local/tmp/nfc_new.conf $existingPath && " +
            "svc nfc disable && sleep 0.4 && svc nfc enable"
        val out = runSuCommand(patchCmd)
        return if (out.contains("ERR:")) {
            false to "Ошибка записи через su: $out"
        } else {
            true to "Аппаратный UID $uidHex применён через Root ($uidSpaced)"
        }
    }

    private fun stopRootUid() {
        val backupPath = rootConfigBackupPath ?: return
        runCatching {
            val target = listOf(
                "/data/adb/modules/nfckeywallet/system/vendor/etc/libnfc-nci.conf",
                "/vendor/etc/libnfc-nci.conf",
                "/system/etc/libnfc-nci.conf"
            ).firstOrNull { runSuCommand("test -w $it && echo W").contains("W") } ?: return

            runSuCommand("cp $backupPath $target && svc nfc disable && sleep 0.4 && svc nfc enable")
        }
    }

    fun isRootAvailable(): Boolean {
        return try {
            val res = runSuCommand("id")
            res.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    private fun runSuCommand(cmd: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val stdout = BufferedReader(InputStreamReader(proc.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(proc.errorStream)).readText()
            val code = proc.waitFor()
            if (code == 0) stdout else "ERR: $stderr"
        } catch (e: Exception) {
            "ERR: ${e.message}"
        }
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------
    fun runDiagnostics(hasWriteSecureSettings: Boolean): UidDiagnosticsReport {
        val adapter = nfcAdapter
        val oemClass = runCatching { Class.forName("android.nfc.NfcOemExtension") }.getOrNull()
        val hasOemClass = oemClass != null

        val oemMethods = mutableListOf<String>()
        var hasEmulateMethod = false

        if (oemClass != null) {
            oemClass.methods.forEach { m ->
                if (m.name.contains("emulate", ignoreCase = true) ||
                    m.name.contains("nci", ignoreCase = true) ||
                    m.name.contains("nfcid", ignoreCase = true) ||
                    m.name.contains("routing", ignoreCase = true)
                ) {
                    oemMethods.add(m.name)
                }
                if (m.name.contains("emulateNfcTechnologyA", ignoreCase = true) ||
                    m.name.contains("setNfcId1", ignoreCase = true)
                ) {
                    hasEmulateMethod = true
                }
            }
        }

        val hasVendorNci = adapter != null && findSendVendorNciMethod(adapter) != null
        val hasVendorCb = adapter != null && adapter.javaClass.methods.any {
            it.name == "registerNfcVendorNciCallback"
        }
        if (hasVendorNci) oemMethods.add("NfcAdapter.sendVendorNciMessage")
        if (hasVendorCb) oemMethods.add("NfcAdapter.registerNfcVendorNciCallback")

        val hasRoot = isRootAvailable()

        val summary = when {
            hasEmulateMethod && hasWriteSecureSettings ->
                "Поддерживается Android 15 OEM Extension (без root)!"
            hasVendorNci && hasWriteSecureSettings ->
                "Доступен Raw NCI (IDLE -> CORE_SET_CONFIG -> RESET_DISC -> GET_AFTER)."
            hasRoot ->
                "Android 15 UID API не найден, но доступен Root (su)."
            else ->
                "Ограничение прошивки — работает стандартный HCE."
        }

        return UidDiagnosticsReport(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
            androidVersion = Build.VERSION.RELEASE ?: "unknown",
            apiLevel = Build.VERSION.SDK_INT,
            hasWriteSecureSettings = hasWriteSecureSettings,
            hasOemExtensionClass = hasOemClass,
            hasOemEmulateMethod = hasEmulateMethod,
            hasVendorNciMethod = hasVendorNci,
            hasVendorNciCallback = hasVendorCb,
            hasRootAccess = hasRoot,
            detectedOemMethods = oemMethods.distinct().take(12),
            lastNciLog = lastNciLog,
            summary = summary
        )
    }
}
