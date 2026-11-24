package com.nfckeywallet.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nfckeywallet.app.model.AppSettings
import com.nfckeywallet.app.model.NfcKey

class KeyRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getKeys(): List<NfcKey> {
        val json = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<NfcKey>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveKeys(keys: List<NfcKey>) {
        prefs.edit().putString(KEY_LIST, gson.toJson(keys)).apply()
    }

    fun addOrUpdateKey(key: NfcKey): List<NfcKey> {
        val current = getKeys().toMutableList()
        val idx = current.indexOfFirst { it.id == key.id }
        if (idx >= 0) {
            current[idx] = key
        } else {
            current.add(0, key)
        }
        saveKeys(current)
        if (getSelectedKeyId() == null) {
            setSelectedKeyId(key.id)
        }
        return current
    }

    fun deleteKey(id: String): List<NfcKey> {
        val updated = getKeys().filterNot { it.id == id }
        saveKeys(updated)
        if (getSelectedKeyId() == id) {
            setSelectedKeyId(updated.firstOrNull()?.id)
        }
        return updated
    }

    fun getSelectedKeyId(): String? = prefs.getString(KEY_SELECTED_ID, null)

    fun setSelectedKeyId(id: String?) {
        prefs.edit().putString(KEY_SELECTED_ID, id).apply()
    }

    fun getSelectedKey(): NfcKey? {
        val keys = getKeys()
        val selectedId = getSelectedKeyId()
        return keys.firstOrNull { it.id == selectedId } ?: keys.firstOrNull()
    }

    fun markKeyUsed(id: String) {
        val keys = getKeys().map {
            if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it
        }
        saveKeys(keys)
    }

    fun isEmulationEnabled(): Boolean = prefs.getBoolean(KEY_EMULATION_ENABLED, true)

    fun setEmulationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMULATION_ENABLED, enabled).apply()
    }

    fun getPreviousPaymentComponent(): String? = prefs.getString(KEY_PREVIOUS_PAYMENT, null)

    fun setPreviousPaymentComponent(component: String?) {
        prefs.edit().putString(KEY_PREVIOUS_PAYMENT, component).apply()
    }

    /** Optional user-pinned payment app to return to (null = auto-detect previous app). */
    fun getPreferredReturnComponent(): String? = prefs.getString(KEY_PREFERRED_RETURN, null)

    fun setPreferredReturnComponent(component: String?) {
        prefs.edit().putString(KEY_PREFERRED_RETURN, component).apply()
    }

    fun getRestoreDeadlineMs(): Long = prefs.getLong(KEY_RESTORE_DEADLINE_MS, 0L)

    fun setRestoreDeadlineMs(deadlineMs: Long) {
        prefs.edit().putLong(KEY_RESTORE_DEADLINE_MS, deadlineMs).apply()
    }

    fun getSettings(): AppSettings {
        val json = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return try {
            gson.fromJson(json, AppSettings::class.java) ?: AppSettings()
        } catch (_: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(settings)).apply()
    }

    fun exportKeysJson(): String = gson.toJson(getKeys())

    fun importKeysJson(json: String): Int {
        return try {
            val type = object : TypeToken<List<NfcKey>>() {}.type
            val imported: List<NfcKey> = gson.fromJson(json, type) ?: return 0
            if (imported.isEmpty()) return 0
            val existing = getKeys().toMutableList()
            val existingIds = existing.map { it.id }.toMutableSet()
            var added = 0
            for (item in imported) {
                if (item.name.isBlank()) continue
                if (existingIds.add(item.id)) {
                    existing.add(item)
                    added++
                }
            }
            if (added > 0) {
                saveKeys(existing)
                if (getSelectedKeyId() == null) {
                    setSelectedKeyId(existing.firstOrNull()?.id)
                }
            }
            added
        } catch (_: Exception) {
            0
        }
    }

    companion object {
        private const val PREFS_NAME = "nfc_key_wallet_prefs"
        private const val KEY_LIST = "keys_json"
        private const val KEY_SELECTED_ID = "selected_key_id"
        private const val KEY_EMULATION_ENABLED = "emulation_enabled"
        private const val KEY_PREVIOUS_PAYMENT = "previous_payment_component"
        private const val KEY_PREFERRED_RETURN = "preferred_return_component"
        private const val KEY_RESTORE_DEADLINE_MS = "restore_deadline_ms"
        private const val KEY_SETTINGS = "app_settings_json"
    }
}
