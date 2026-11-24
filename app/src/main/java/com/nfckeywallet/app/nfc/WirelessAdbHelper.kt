package com.nfckeywallet.app.nfc

import android.Manifest
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import sun.security.x509.AlgorithmId
import sun.security.x509.CertificateAlgorithmId
import sun.security.x509.CertificateExtensions
import sun.security.x509.CertificateIssuerName
import sun.security.x509.CertificateSerialNumber
import sun.security.x509.CertificateSubjectName
import sun.security.x509.CertificateValidity
import sun.security.x509.CertificateVersion
import sun.security.x509.CertificateX509Key
import sun.security.x509.KeyIdentifier
import sun.security.x509.PrivateKeyUsageExtension
import sun.security.x509.SubjectKeyIdentifierExtension
import sun.security.x509.X500Name
import sun.security.x509.X509CertImpl
import sun.security.x509.X509CertInfo
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.util.Date
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local Wireless ADB client (Android 11+) using libadb-android.
 * Connects strictly to 127.0.0.1 to run:
 *   pm grant com.nfckeywallet.app android.permission.WRITE_SECURE_SETTINGS
 * without needing a computer or third-party apps.
 */
class WirelessAdbHelper private constructor() : AbsAdbConnectionManager() {

    private val privateKeyObj: PrivateKey
    private val certificateObj: Certificate

    init {
        api = Build.VERSION.SDK_INT
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val pair = kpg.generateKeyPair()
        privateKeyObj = pair.private

        val subject = "CN=mifareRill"
        val algorithmName = "SHA512withRSA"
        val notBefore = Date()
        val notAfter = Date(System.currentTimeMillis() + 365L * 86400L * 1000L)

        val extensions = CertificateExtensions()
        extensions.set(
            "SubjectKeyIdentifier",
            SubjectKeyIdentifierExtension(KeyIdentifier(pair.public).identifier)
        )
        extensions.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))

        val x500Name = X500Name(subject)
        val certInfo = X509CertInfo()
        certInfo.set("version", CertificateVersion(2))
        certInfo.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
        certInfo.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
        certInfo.set("subject", CertificateSubjectName(x500Name))
        certInfo.set("key", CertificateX509Key(pair.public))
        certInfo.set("validity", CertificateValidity(notBefore, notAfter))
        certInfo.set("issuer", CertificateIssuerName(x500Name))
        certInfo.set("extensions", extensions)

        val cert = X509CertImpl(certInfo)
        cert.sign(privateKeyObj, algorithmName)
        certificateObj = cert
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
         * Attempts to auto-discover the local Wireless Debugging pairing port via mDNS.
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
                        manager.autoConnect(context.applicationContext, 5000)
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
