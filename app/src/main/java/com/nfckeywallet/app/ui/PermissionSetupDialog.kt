package com.nfckeywallet.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfckeywallet.app.nfc.DefaultPaymentHelper
import com.nfckeywallet.app.nfc.PermissionGrantManager
import com.nfckeywallet.app.ui.theme.StealthColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSetupSheet(
    hasWriteSecureSettings: Boolean,
    isBusy: Boolean,
    statusMessage: String?,
    onAutoSetup: () -> Unit,
    onStartWifiNotificationWizard: () -> Unit,
    onGrantViaWifiManual: (pairingPort: Int, pairingCode: String, connectPort: Int) -> Unit,
    onGrantViaShizuku: () -> Unit,
    onGrantViaRoot: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    val shizukuRunning = remember { PermissionGrantManager.isShizukuAvailable() }
    val rootDetected = remember { PermissionGrantManager.isRootLikelyAvailable() }
    val isXiaomi = remember { PermissionGrantManager.isXiaomiDevice() }

    var showManualOptions by remember { mutableStateOf(false) }
    var showWifiManualFields by remember { mutableStateOf(false) }
    var pairingCode by remember { mutableStateOf("") }
    var pairingPortText by remember { mutableStateOf("") }
    var connectPortText by remember { mutableStateOf("") }
    var copiedCmd by remember { mutableStateOf(false) }

    val autoMethodSubtitle = when {
        shizukuRunning -> "Обнаружен Shizuku — выдам право в 1 нажатие"
        rootDetected -> "Обнаружен Root (su) — выдам право в 1 нажатие"
        else -> "Запущу поиск Wi-Fi и открою Настройки — введите только 6 цифр в уведомлении"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StealthColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Выдача права без ПК",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = StealthColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Разовая настройка WRITE_SECURE_SETTINGS для аппаратного UID и авто-возврата оплаты.",
                style = MaterialTheme.typography.bodySmall,
                color = StealthColors.TextSecondary
            )

            if (hasWriteSecureSettings) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = StealthColors.EmeraldSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StealthColors.EmeraldBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = StealthColors.EmeraldBright,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Право уже выдано! Всё готово к работе.",
                            fontWeight = FontWeight.SemiBold,
                            color = StealthColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = StealthColors.SurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StealthColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = StealthColors.TextPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Primary Smart Auto-Setup Card (Права 1)
            Surface(
                color = StealthColors.EmeraldSurface.copy(alpha = 0.65f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, StealthColors.EmeraldBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            tint = StealthColors.EmeraldBright,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Быстрая авто-настройка",
                            fontWeight = FontWeight.Bold,
                            color = StealthColors.TextPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = autoMethodSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = StealthColors.TextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    UnifiedSetupButton(
                        text = "Настроить автоматически",
                        icon = Icons.Rounded.AutoFixHigh,
                        isPrimary = true,
                        isBusy = isBusy,
                        onClick = onAutoSetup
                    )
                }
            }

            if (isXiaomi) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = StealthColors.CrimsonSurface.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StealthColors.CrimsonBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = StealthColors.CrimsonBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Xiaomi / HyperOS: включите в «Для разработчиков» пункт «Отладка по USB (Настройки безопасности)», иначе система заблокирует pm grant.",
                            style = MaterialTheme.typography.labelSmall,
                            color = StealthColors.TextPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Collapsible manual methods section
            Surface(
                color = StealthColors.SurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, StealthColors.Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showManualOptions = !showManualOptions }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Выбрать способ вручную (Wi-Fi / Shizuku / Root / ПК)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = StealthColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showManualOptions) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = StealthColors.TextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = showManualOptions) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Method 1: Wireless ADB via Notification (RemoteInput)
                    MethodCard(
                        icon = Icons.Rounded.Wifi,
                        iconTint = StealthColors.EmeraldBright,
                        title = "1. По Wi-Fi (уведомление с кодом)",
                        badge = "Без ПК",
                        badgeColor = StealthColors.EmeraldBright,
                        description = "Без разделённого экрана и ввода портов: откройте «Отладка по Wi-Fi» → «Подключить по коду» и вбейте 6 цифр в уведомлении сверху."
                    ) {
                        UnifiedSetupButton(
                            text = "Запустить Wi-Fi уведомление",
                            icon = Icons.Rounded.NotificationsActive,
                            isPrimary = false,
                            isBusy = isBusy,
                            onClick = onStartWifiNotificationWizard
                        )

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (showWifiManualFields) "Скрыть ручной ввод порта" else "Ввести код и порт вручную ▾",
                            style = MaterialTheme.typography.labelSmall,
                            color = StealthColors.TextSecondary,
                            modifier = Modifier
                                .clickable { showWifiManualFields = !showWifiManualFields }
                                .padding(vertical = 4.dp)
                        )

                        AnimatedVisibility(visible = showWifiManualFields) {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pairingCode,
                                        onValueChange = { pairingCode = it.filter { c -> c.isDigit() }.take(6) },
                                        label = { Text("Код (6 цифр)") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = pairingPortText,
                                        onValueChange = { pairingPortText = it.filter { c -> c.isDigit() }.take(5) },
                                        label = { Text("Порт сопр.") },
                                        placeholder = { Text("Авто") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = connectPortText,
                                    onValueChange = { connectPortText = it.filter { c -> c.isDigit() }.take(5) },
                                    label = { Text("Порт подключения (авто)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                UnifiedSetupButton(
                                    text = "Подключить и выдать право",
                                    icon = Icons.Rounded.Wifi,
                                    isPrimary = false,
                                    isBusy = isBusy,
                                    onClick = {
                                        onGrantViaWifiManual(
                                            pairingPortText.toIntOrNull() ?: 0,
                                            pairingCode.trim(),
                                            connectPortText.toIntOrNull() ?: 0
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Method 2: Shizuku (1 tap)
                    MethodCard(
                        icon = Icons.Rounded.AdminPanelSettings,
                        iconTint = StealthColors.EmeraldBright,
                        title = "2. Через Shizuku",
                        badge = if (shizukuRunning) "Запущен" else "Не запущен",
                        badgeColor = if (shizukuRunning) StealthColors.EmeraldBright else StealthColors.TextMuted,
                        description = "Мгновенная выдача в 1 нажатие, если служба Shizuku активна."
                    ) {
                        UnifiedSetupButton(
                            text = "Выдать через Shizuku",
                            icon = Icons.Rounded.AdminPanelSettings,
                            isPrimary = false,
                            isBusy = isBusy,
                            onClick = onGrantViaShizuku
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Method 3: Root (su)
                    MethodCard(
                        icon = Icons.Rounded.Terminal,
                        iconTint = StealthColors.CrimsonBright,
                        title = "3. Через Root (Magisk / KernelSU)",
                        badge = if (rootDetected) "Найден" else "Нет su",
                        badgeColor = if (rootDetected) StealthColors.EmeraldBright else StealthColors.TextMuted,
                        description = "Выполнит pm grant через системный su."
                    ) {
                        UnifiedSetupButton(
                            text = "Выдать через Root (su)",
                            icon = Icons.Rounded.Terminal,
                            isPrimary = false,
                            isBusy = isBusy,
                            onClick = onGrantViaRoot
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Fallback: PC ADB command
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, StealthColors.BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = DefaultPaymentHelper.ADB_GRANT_COMMAND,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = StealthColors.EmeraldBright,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    UnifiedSetupButton(
                        text = if (copiedCmd) "Команда скопирована ✓" else "Скопировать команду для ПК",
                        icon = Icons.Rounded.ContentCopy,
                        isPrimary = false,
                        isBusy = false,
                        onClick = {
                            clipboard.setText(AnnotatedString(DefaultPaymentHelper.ADB_GRANT_COMMAND))
                            copiedCmd = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    badge: String,
    badgeColor: Color,
    description: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = StealthColors.SurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, StealthColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        color = StealthColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badge,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = StealthColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/**
 * Strictly uniform action button (48.dp height, 12.dp radius, single-line aligned label)
 * used across all permission setup methods so buttons never drift or differ in size.
 */
@Composable
private fun UnifiedSetupButton(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isPrimary) StealthColors.Emerald else Color(0xFF1A222C)
    val contentColor = if (isPrimary) Color(0xFF031F16) else StealthColors.TextPrimary
    val border = if (isPrimary) null else BorderStroke(1.dp, StealthColors.Border)

    Button(
        onClick = onClick,
        enabled = !isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = border,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        )
    ) {
        if (isBusy && isPrimary) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
