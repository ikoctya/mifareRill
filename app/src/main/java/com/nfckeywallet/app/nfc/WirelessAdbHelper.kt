package com.nfckeywallet.app.nfc

import android.Manifest
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local Wireless ADB client (Android 11+) using libadb-android + BouncyCastle X.509 certificate.
 * Connects strictly to 127.0.0.1 to run:
 *   pm grant com.nfckeywallet.app android.permission.WRITE_SECURE_SETTINGS
 * without needing a computer or third-party apps.
 */
class WirelessAdbHelper private constructor() : AbsAdbConnectionManager() {

    private val privateKeyObj: PrivateKey
    private val certificateObj: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val pair = kpg.generateKeyPair()
        privateKeyObj = pair.private

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 60_000L)
        val notAfter = Date(now + 365L * 86_400_000L)
        val subject = X500Name("CN=mifareRill")
        val serial = BigInteger(64, SecureRandom()).abs()

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            pair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKeyObj)
        certificateObj = JcaX509CertificateConverter().getCertificate(certBuilder.build(signer))
    }

    override fun getPrivateKey(): PrivateKey = privateKeyObj

    override fun getCertificate(): Certificate = certificateObj

    override fun getDeviceName(): String = "mifareRill"

    companion object {
        @Volatile
        private var instance: WirelessAdbHelper? = null

        fun getInstance(): WirelessAdbHelper {
            return instance ?: synchronized(this) {
                instance ?: WirelessAdbHelper().also { instance = it }
            }
        }

        /**
         * Attempts to auto-discover the local Wireless Debugging port via mDNS.
         */
        fun discoverLocalPort(context: Context, serviceType: String, timeoutMs: Long = 2500L): Int {
            val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return 0
            val latch = CountDownLatch(1)
            val foundPort = AtomicInteger(0)

            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(si: NsdServiceInfo) {
                                if (si.port > 0) {
                                    foundPort.set(si.port)
                                    latch.countDown()
                                }
                            }
                        })
                    }
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    latch.countDown()
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }

            return try {
                nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                foundPort.get()
            } catch (_: Exception) {
                0
            } finally {
                runCatching { nsd.stopServiceDiscovery(discoveryListener) }
            }
        }

        /**
         * Pairs (if pairingCode provided), connects to local Wireless ADB on 127.0.0.1,
         * and executes `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`.
         */
        fun pairAndGrantPermission(
            context: Context,
            pairingPortInput: Int,
            pairingCode: String,
            connectPortInput: Int = 0
        ): Pair<Boolean, String> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return false to "Отладка по Wi-Fi поддерживается начиная с Android 11"
            }
            return try {
                val manager = getInstance()
                val cleanCode = pairingCode.replace(Regex("[^0-9]"), "")

                // 1. Pair if code was provided
                if (cleanCode.isNotEmpty()) {
                    val pPort = if (pairingPortInput > 0) {
                        pairingPortInput
                    } else {
                        discoverLocalPort(context, "_adb-tls-pairing._tcp.")
                    }
                    if (pPort <= 0) {
                        return false to "Не найден порт сопряжения. Укажите 5-значный порт из окна «Подключить по коду»."
                    }
                    val paired = manager.pair("127.0.0.1", pPort, cleanCode)
                    if (!paired) {
                        return false to "Не удалось выполнить сопряжение (проверьте код и порт $pPort)"
                    }
                }

                // 2. Connect to local Wireless ADB daemon
                val connected = if (connectPortInput > 0) {
                    manager.connect("127.0.0.1", connectPortInput)
                } else {
                    val discoveredConnect = discoverLocalPort(context, "_adb-tls-connect._tcp.")
                    if (discoveredConnect > 0) {
                        manager.connect("127.0.0.1", discoveredConnect)
                    } else {
                        manager.autoConnect(context.applicationContext, 5000L)
                    }
                }
                if (!connected) {
                    return false to "Сопряжение прошло, но не удалось подключиться к ADB. Включите «Отладку по Wi-Fi» и укажите порт подключения."
                }

                // 3. Grant WRITE_SECURE_SETTINGS via ADB shell
                val cmd = "pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"
                val stream = manager.openStream("shell:$cmd")
                val output = runCatching {
                    BufferedReader(InputStreamReader(stream.openInputStream())).readText().trim()
                }.getOrDefault("")
                runCatching { stream.close() }
                runCatching { manager.disconnect() }

                val granted = DefaultPaymentHelper(context).hasWriteSecureSettingsPermission()
                if (granted) {
                    true to "Право WRITE_SECURE_SETTINGS успешно выдано по Wi-Fi! Отладку по Wi-Fi можно выключить."
                } else if (output.isNotBlank()) {
                    false to "Ответ системы: $output (на Xiaomi включите «Отладка по USB — Настройки безопасности»)"
                } else {
                    false to "Команда выполнена, но право не появилось (на Xiaomi включите «Отладка по USB — Настройки безопасности»)"
                }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                false to "Ошибка Wi-Fi ADB: $msg"
            }
        }
    }
}
