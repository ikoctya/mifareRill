package com.nfckeywallet.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfckeywallet.app.model.AppSettings
import com.nfckeywallet.app.nfc.DefaultPaymentHelper
import com.nfckeywallet.app.nfc.InstalledPaymentApp
import com.nfckeywallet.app.nfc.UidBackendType
import com.nfckeywallet.app.nfc.UidDiagnosticsReport

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    hasWriteSecureSettings: Boolean,
    isSystemDefaultWallet: Boolean,
    installedPaymentApps: List<InstalledPaymentApp>,
    preferredReturnComponent: String?,
    autoDetectedPreviousLabel: String,
    currentSystemLabel: String,
    diagnosticsReport: UidDiagnosticsReport?,
    backupMessage: String?,
    onRunDiagnostics: () -> Unit,
    onExportKeys: () -> Unit,
    onImportKeys: () -> Unit,
    onSelectReturnComponent: (String?) -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onRequestSystemDefaultWallet: () -> Unit,
    onRecheckPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    var copiedAdb by remember { mutableStateOf(false) }
    var copiedDiag by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Настройки переключения NFC",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))

            // Real System Default Switch Status Card
            Surface(
                color = if (hasWriteSecureSettings) {
                    Color(0xFF10B981).copy(alpha = 0.14f)
                } else {
                    Color(0xFFFFB77C).copy(alpha = 0.14f)
                },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (hasWriteSecureSettings) Color(0xFF10B981).copy(alpha = 0.5f)
                    else Color(0xFFFFB77C).copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasWriteSecureSettings) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.WarningAmber
                            },
                            contentDescription = null,
                            tint = if (hasWriteSecureSettings) Color(0xFF5CE1B8) else Color(0xFFFFB77C),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (hasWriteSecureSettings) {
                                "Системное авто-переключение активно"
                            } else {
                                "Нужно разовое ADB-разрешение (без root)"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (hasWriteSecureSettings) {
                            "Сейчас в системе: $currentSystemLabel\nЗапомнено для возврата: $autoDetectedPreviousLabel"
                        } else {
                            "Android запрещает обычным приложениям менять системный кошелёк без этого права. Выполните команду 1 раз в терминале:"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!hasWriteSecureSettings) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = Color.Black.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = DefaultPaymentHelper.ADB_GRANT_COMMAND,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF93C5FD),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(DefaultPaymentHelper.ADB_GRANT_COMMAND))
                                    copiedAdb = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (copiedAdb) "Скопировано" else "Копировать")
                            }
                            OutlinedButton(
                                onClick = onRecheckPermission,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Проверить")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Hardware UID Emulation (Android 15+ OEM / Raw NCI / Root)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Эмуляция аппаратного UID",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    SettingToggleRow(
                        title = "Подменять аппаратный UID (NFCID1)",
                        subtitle = "Android 15+ OEM Extension / Raw NCI (без root) или Root (su)",
                        checked = settings.enableHardwareUidEmulation,
                        onCheckedChange = {
                            onUpdateSettings(settings.copy(enableHardwareUidEmulation = it))
                        }
                    )

                    if (settings.enableHardwareUidEmulation) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Движок подмены UID:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UidBackendType.entries.forEach { backend ->
                                FilterChip(
                                    selected = settings.uidBackendPreference == backend.id,
                                    onClick = {
                                        onUpdateSettings(settings.copy(uidBackendPreference = backend.id))
                                    },
                                    label = { Text(backend.title, fontSize = 12.sp, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = {
                            copiedDiag = false
                            onRunDiagnostics()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Проверить поддержку UID на этом телефоне")
                    }

                    if (diagnosticsReport != null) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = Color.Black.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = diagnosticsReport.toCopyableText().trim(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(diagnosticsReport.toCopyableText()))
                                copiedDiag = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (copiedDiag) "Отчёт скопирован" else "Скопировать отчёт диагностики")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Choose which payment app to return to (deduplicated by packageName)
            Text(
                text = "Возвращать приложение оплаты",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "«Авто» возвращает то, что стояло до запуска. Или выберите конкретный кошелёк ниже:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = preferredReturnComponent == null,
                    onClick = { onSelectReturnComponent(null) },
                    label = { Text("Авто", maxLines = 1, softWrap = false) }
                )
                installedPaymentApps.forEach { app ->
                    FilterChip(
                        selected = preferredReturnComponent == app.componentFlat,
                        onClick = { onSelectReturnComponent(app.componentFlat) },
                        label = { Text(app.label, maxLines = 1, softWrap = false) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Таймер возврата к приложению оплаты",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Через сколько секунд автоматически вернуть приложение оплаты (работает даже в фоне)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            val timerOptions = listOf(10 to "10с", 20 to "20с", 30 to "30с", 60 to "60с", 0 to "Вручную")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                timerOptions.forEach { (sec, label) ->
                    FilterChip(
                        selected = settings.autoReleaseSeconds == sec,
                        onClick = { onUpdateSettings(settings.copy(autoReleaseSeconds = sec)) },
                        label = { Text(label, maxLines = 1, softWrap = false) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SettingToggleRow(
                title = "Активировать при открытии",
                subtitle = "Автоматически переключать NFC на «NFC Ключницу» при запуске",
                checked = settings.autoActivateOnOpen,
                onCheckedChange = { onUpdateSettings(settings.copy(autoActivateOnOpen = it)) }
            )

            SettingToggleRow(
                title = "Возвращать оплату после срабатывания",
                subtitle = "Через 3 секунды после успешного поднесения вернуть кошелёк",
                checked = settings.releaseAfterTap,
                onCheckedChange = { onUpdateSettings(settings.copy(releaseAfterTap = it)) }
            )

            SettingToggleRow(
                title = "Вибрация при срабатывании",
                subtitle = "Тактильный отклик, когда считыватель принял ключ",
                checked = settings.vibrateOnTap,
                onCheckedChange = { onUpdateSettings(settings.copy(vibrateOnTap = it)) }
            )

            Spacer(Modifier.height(14.dp))

            // Backup & Restore Keys (JSON)
            Text(
                text = "Резервная копия ключей",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = onExportKeys,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Экспорт JSON", maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                    onClick = onImportKeys,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Импорт JSON", maxLines = 1, softWrap = false)
                }
            }
            if (!backupMessage.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = backupMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5CE1B8)
                )
            }

            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                onClick = onRequestSystemDefaultWallet,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (isSystemDefaultWallet) {
                        "Сейчас выбрано в системе (изменить вручную)"
                    } else {
                        "Открыть системный выбор кошелька Android"
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
