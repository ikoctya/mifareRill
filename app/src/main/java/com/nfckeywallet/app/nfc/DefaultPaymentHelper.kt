package com.nfckeywallet.app.nfc

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.nfckeywallet.app.data.KeyRepository

data class InstalledPaymentApp(
    val label: String,
    val packageName: String,
    val componentFlat: String
)

class DefaultPaymentHelper(context: Context) {

    private val appContext = context.applicationContext
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(appContext)
    private val cardEmulation: CardEmulation? =
        nfcAdapter?.let { runCatching { CardEmulation.getInstance(it) }.getOrNull() }
    val serviceComponent = ComponentName(appContext, KeyHceService::class.java)
    val serviceComponentFlat: String = serviceComponent.flattenToString()

    fun isNfcSupported(): Boolean = nfcAdapter != null

    fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled == true

    fun hasWriteSecureSettingsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Detects the REAL active default payment service, even on Android 14-16 where
     * Settings.Secure.nfc_payment_default_component can be null because RoleManager.ROLE_WALLET
     * stores the wallet separately.
     */
    fun getCurrentSystemPaymentComponent(): String? {
        val secureValue = runCatching {
            Settings.Secure.getString(appContext.contentResolver, NFC_PAYMENT_DEFAULT_COMPONENT)
        }.getOrNull()
        if (!secureValue.isNullOrBlank()) {
            return secureValue
        }

        // Android 14-16 RoleManager fallback: query every installed HCE service via CardEmulation
        val ce = cardEmulation ?: return null
        return try {
            if (ce.isDefaultServiceForCategory(serviceComponent, CardEmulation.CATEGORY_PAYMENT)) {
                return serviceComponentFlat
            }
            for (svc in queryAllExternalHceServices()) {
                val cn = ComponentName.unflattenFromString(svc.componentFlat) ?: continue
                if (ce.isDefaultServiceForCategory(cn, CardEmulation.CATEGORY_PAYMENT)) {
                    return svc.componentFlat
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun isOurComponent(componentString: String?): Boolean {
        if (componentString.isNullOrBlank()) return false
        return componentString == serviceComponentFlat ||
            componentString == serviceComponent.flattenToShortString() ||
            componentString.startsWith("${appContext.packageName}/")
    }

    fun isSystemDefaultPaymentApp(): Boolean {
        val secureValue = runCatching {
            Settings.Secure.getString(appContext.contentResolver, NFC_PAYMENT_DEFAULT_COMPONENT)
        }.getOrNull()
        if (isOurComponent(secureValue)) return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val roleManager = appContext.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_WALLET) == true ||
                    cardEmulation?.isDefaultServiceForCategory(
                        serviceComponent,
                        CardEmulation.CATEGORY_PAYMENT
                    ) == true
            } else {
                cardEmulation?.isDefaultServiceForCategory(
                    serviceComponent,
                    CardEmulation.CATEGORY_PAYMENT
                ) == true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun queryAllExternalHceServices(): List<InstalledPaymentApp> {
        return try {
            val pm = appContext.packageManager
            val intent = Intent(HostApduService.SERVICE_INTERFACE)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
            }
            resolved.mapNotNull { info ->
                val svc = info.serviceInfo ?: return@mapNotNull null
                if (svc.packageName == appContext.packageName) return@mapNotNull null
                val cn = ComponentName(svc.packageName, svc.name)
                val appLabel = runCatching {
                    svc.applicationInfo?.let { pm.getApplicationLabel(it).toString() }
                        ?: info.loadLabel(pm).toString()
                }.getOrDefault(svc.packageName)
                InstalledPaymentApp(
                    label = appLabel,
                    packageName = svc.packageName,
                    componentFlat = cn.flattenToString()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Lists installed NFC payment apps deduplicated by packageName (no 5x Google Play Services / 4x Alipay).
     * If a package has multiple HCE services, prefers the one currently marked default for payment.
     */
    fun listInstalledPaymentApps(): List<InstalledPaymentApp> {
        val all = queryAllExternalHceServices()
        val ce = cardEmulation
        return all
            .groupBy { it.packageName }
            .values
            .map { group ->
                group.firstOrNull { app ->
                    val cn = ComponentName.unflattenFromString(app.componentFlat)
                    cn != null && runCatching {
                        ce?.isDefaultServiceForCategory(cn, CardEmulation.CATEGORY_PAYMENT) == true
                    }.getOrDefault(false)
                } ?: group.first()
            }
            .sortedBy { it.label.lowercase() }
    }

    fun labelForComponent(componentFlat: String?): String {
        if (componentFlat.isNullOrBlank()) return "Не определено"
        if (isOurComponent(componentFlat)) return "NFC Ключница"
        val cn = ComponentName.unflattenFromString(componentFlat) ?: return componentFlat
        return try {
            val pm = appContext.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(cn.packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(cn.packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            cn.packageName
        }
    }

    /**
     * Switches the REAL system-wide Default Payment App to this app if WRITE_SECURE_SETTINGS
     * is granted, saving the real previously active payment app first.
     */
    fun switchSystemDefaultToSelf(repository: KeyRepository): Boolean {
        if (!hasWriteSecureSettingsPermission()) return false
        return try {
            val current = getCurrentSystemPaymentComponent()
            if (!current.isNullOrBlank() && !isOurComponent(current)) {
                repository.setPreviousPaymentComponent(current)
            }
            Settings.Secure.putString(
                appContext.contentResolver,
                NFC_PAYMENT_DEFAULT_COMPONENT,
                serviceComponentFlat
            )
            isOurComponent(
                Settings.Secure.getString(appContext.contentResolver, NFC_PAYMENT_DEFAULT_COMPONENT)
            )
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Restores the payment app in priority order:
     * 1. User-pinned return app in Settings (if explicitly chosen)
     * 2. Auto-saved previous payment app before switching
     * Never falls back to a random alphabetical app (fixes Mir Pay replacing Yandex Pay).
     */
    fun restorePreviousSystemDefault(repository: KeyRepository): Boolean {
        if (!hasWriteSecureSettingsPermission()) return false

        val pinned = repository.getPreferredReturnComponent()
        val previous = repository.getPreviousPaymentComponent()
        val target = when {
            !pinned.isNullOrBlank() && !isOurComponent(pinned) -> pinned
            !previous.isNullOrBlank() && !isOurComponent(previous) -> previous
            else -> null
        } ?: return false

        return try {
            Settings.Secure.putString(
                appContext.contentResolver,
                NFC_PAYMENT_DEFAULT_COMPONENT,
                target
            )
            val after = Settings.Secure.getString(
                appContext.contentResolver,
                NFC_PAYMENT_DEFAULT_COMPONENT
            )
            !isOurComponent(after)
        } catch (_: Exception) {
            false
        }
    }

    fun activateForegroundPriority(activity: Activity): Boolean {
        val ce = cardEmulation ?: return false
        return try {
            ce.setPreferredService(activity, serviceComponent)
        } catch (_: Exception) {
            false
        }
    }

    fun releaseForegroundPriority(activity: Activity): Boolean {
        val ce = cardEmulation ?: return false
        return try {
            ce.unsetPreferredService(activity)
        } catch (_: Exception) {
            false
        }
    }

    fun syncCustomAids(customAids: List<String>) {
        val ce = cardEmulation ?: return
        val validAids = customAids
            .map { it.replace(Regex("[^0-9A-Fa-f]"), "").uppercase() }
            .filter { it.length in 10..32 && it.length % 2 == 0 }
            .distinct()
        if (validAids.isEmpty()) return
        try {
            ce.registerAidsForService(serviceComponent, CardEmulation.CATEGORY_OTHER, validAids)
        } catch (_: Exception) {
        }
    }

    fun buildSystemDefaultPaymentIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val roleManager = appContext.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_WALLET)) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_WALLET)
            }
        }
        return Intent(CardEmulation.ACTION_CHANGE_DEFAULT).apply {
            putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT)
            putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, serviceComponent)
        }
    }

    companion object {
        const val NFC_PAYMENT_DEFAULT_COMPONENT = "nfc_payment_default_component"
        const val ADB_GRANT_COMMAND =
            "adb shell pm grant com.nfckeywallet.app android.permission.WRITE_SECURE_SETTINGS"
    }
}
