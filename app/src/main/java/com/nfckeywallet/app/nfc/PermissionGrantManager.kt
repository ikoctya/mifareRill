package com.nfckeywallet.app.nfc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Unified helper for granting android.permission.WRITE_SECURE_SETTINGS directly on-device:
 * 1. Built-in Wireless Debugging (Android 11+, no PC, no third-party apps)
 * 2. Shizuku (1-tap via Shizuku binder)
 * 3. Root (1-tap via su)
 */
object PermissionGrantManager {

    const val SHIZUKU_PERMISSION_REQUEST_CODE = 4210

    fun isShizukuAvailable(): Boolean {
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    fun hasShizukuPermission(): Boolean {
        return runCatching {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun requestShizukuPermission(): Boolean {
        return runCatching {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            true
        }.getOrDefault(false)
    }

    /**
     * Grants WRITE_SECURE_SETTINGS using Shizuku (must already have Shizuku permission).
     */
    fun grantViaShizuku(context: Context): Pair<Boolean, String> {
        if (!isShizukuAvailable()) {
            return false to "Shizuku не запущен. Откройте приложение Shizuku и нажмите «Запустить»."
        }
        if (!hasShizukuPermission()) {
            requestShizukuPermission()
            return false to "Подтвердите запрос Shizuku во всплывающем окне и нажмите ещё раз."
        }
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val cmd = arrayOf(
                "pm",
                "grant",
                context.packageName,
                Manifest.permission.WRITE_SECURE_SETTINGS
            )
            val proc = newProcessMethod.invoke(null, cmd, null, null) as Process
            val stderr = BufferedReader(InputStreamReader(proc.errorStream)).readText().trim()
            proc.waitFor()

            val granted = DefaultPaymentHelper(context).hasWriteSecureSettingsPermission()
            if (granted) {
                true to "Право WRITE_SECURE_SETTINGS успешно выдано через Shizuku!"
            } else {
                false to (stderr.ifBlank {
                    "Shizuku выполнил команду, но право не появилось (на Xiaomi включите «Отладка по USB — Настройки безопасности»)"
                })
            }
        } catch (e: Exception) {
            val cause = e.cause ?: e
            false to "Ошибка Shizuku: ${cause.message ?: cause.javaClass.simpleName}"
        }
    }

    /**
     * Grants WRITE_SECURE_SETTINGS via Root (su).
     */
    fun grantViaRoot(context: Context): Pair<Boolean, String> {
        return try {
            val cmd = "pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val stderr = BufferedReader(InputStreamReader(proc.errorStream)).readText().trim()
            val exitCode = proc.waitFor()
            val granted = DefaultPaymentHelper(context).hasWriteSecureSettingsPermission()
            if (granted) {
                true to "Право WRITE_SECURE_SETTINGS успешно выдано через Root!"
            } else {
                false to (stderr.ifBlank { "Root-доступ не получен (код $exitCode)" })
            }
        } catch (e: Exception) {
            false to "Root (su) не найден на устройстве"
        }
    }
}
