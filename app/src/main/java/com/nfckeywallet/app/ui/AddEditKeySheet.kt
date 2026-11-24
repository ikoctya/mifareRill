package com.nfckeywallet.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfckeywallet.app.model.EmulationMode
import com.nfckeywallet.app.model.NfcKey
import com.nfckeywallet.app.nfc.HexUtils
import com.nfckeywallet.app.nfc.ScannedTagResult
import com.nfckeywallet.app.ui.components.KeyPresets

private data class MifareProfileOption(
    val label: String,
    val sakHex: String,
    val atqaHex: String
)

private val MIFARE_PROFILES = listOf(
    MifareProfileOption("HCE + UID (20)", "20", "0400"),
    MifareProfileOption("MIFARE+HCE (28)", "28", "0400"),
    MifareProfileOption("MIFARE 1K (08)", "08", "0400"),
    MifareProfileOption("Ultralight (00)", "00", "4400")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditKeySheet(
    editingKey: NfcKey?,
    scannedTag: ScannedTagResult?,
    onDismiss: () -> Unit,
    onSave: (NfcKey) -> Unit,
    onDelete: ((String) -> Unit)?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember(editingKey) { mutableStateOf(editingKey?.name ?: "") }
    var subtitle by remember(editingKey) { mutableStateOf(editingKey?.subtitle ?: "") }
    var iconId by remember(editingKey) { mutableStateOf(editingKey?.iconId ?: "key") }
    var colorId by remember(editingKey) { mutableStateOf(editingKey?.colorId ?: "indigo") }
    var uidHex by remember(editingKey) { mutableStateOf(editingKey?.uidHex ?: "") }
    var reverseOctets by remember(editingKey) { mutableStateOf(editingKey?.reverseOctets ?: false) }
    var atqaHex by remember(editingKey) { mutableStateOf(editingKey?.safeAtqaHex() ?: "0400") }
    var sakHex by remember(editingKey) { mutableStateOf(editingKey?.safeSakHex() ?: "28") }
    var techList by remember(editingKey) { mutableStateOf(editingKey?.techList ?: emptyList()) }
    var emulationMode by remember(editingKey) {
        mutableStateOf(editingKey?.emulationMode ?: EmulationMode.NDEF_TYPE4)
    }
    var customAidHex by remember(editingKey) {
        mutableStateOf(editingKey?.customAidHex ?: "F0010203040506")
    }
    var ndefRawHex by remember(editingKey) { mutableStateOf(editingKey?.ndefRawHex ?: "") }
    var ndefDisplayText by remember(editingKey) { mutableStateOf(editingKey?.ndefDisplayText ?: "") }
    var isHardwareUidOnlyWarning by remember(editingKey) {
        mutableStateOf(editingKey?.isHardwareUidOnlyWarning ?: false)
    }

    LaunchedEffect(scannedTag) {
        if (scannedTag != null) {
            uidHex = scannedTag.uidHex
            atqaHex = scannedTag.atqaHex
            sakHex = scannedTag.sakHex
            techList = scannedTag.techList
            ndefRawHex = scannedTag.ndefRawHex
            ndefDisplayText = scannedTag.ndefDisplayText
            emulationMode = scannedTag.suggestedMode
            if (scannedTag.detectedAidHex.isNotBlank() && scannedTag.detectedAidHex != "D2760000850101") {
                customAidHex = scannedTag.detectedAidHex
            }
            isHardwareUidOnlyWarning = scannedTag.isHardwareUidOnlyWarning
            if (name.isBlank()) {
                name = if (scannedTag.ndefDisplayText.isNotBlank()) {
                    scannedTag.ndefDisplayText.take(24)
                } else {
                    "Ключ ${scannedTag.uidHex.takeLast(5)}"
                }
            }
        }
    }

    val effectiveUidPreview = remember(uidHex, reverseOctets) {
        if (uidHex.isBlank()) ""
        else if (reverseOctets) HexUtils.reverseOctets(uidHex, ":")
        else uidHex
    }

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
                text = if (editingKey == null) "Новый NFC-ключ" else "Редактировать ключ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(14.dp))

            // NFC Scan Status Card
            Surface(
                color = if (uidHex.isNotBlank()) {
                    Color(0xFF10B981).copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (uidHex.isNotBlank()) Color(0xFF10B981).copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Nfc,
                        contentDescription = null,
                        tint = if (uidHex.isNotBlank()) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uidHex.isNotBlank()) {
                                "Метка: $effectiveUidPreview"
                            } else {
                                "Поднесите NFC-метку к телефону"
                            },
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (techList.isNotEmpty()) {
                                "SAK=$sakHex, ATQA=$atqaHex • ${techList.joinToString(", ")}"
                            } else {
                                "Сканер активен — приложите ключ или заполните вручную"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isHardwareUidOnlyWarning) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Метка типа MIFARE/домофон по UID. Для неё используется Raw NCI эмуляция UID + SAK ($sakHex) + ATQA ($atqaHex).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название ключа") },
                placeholder = { Text("Например: Подъезд, Офис, Шлагбаум") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = { Text("Подпись (необязательно)") },
                placeholder = { Text("Например: Домофон Beward") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = uidHex,
                onValueChange = { uidHex = it.uppercase() },
                label = { Text("UID ключа (HEX)") },
                placeholder = { Text("Например: 74:AE:4D:53") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(10.dp))

            // Reverse octets per-key toggle
            Surface(
                color = if (reverseOctets) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (reverseOctets) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = if (reverseOctets) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Перевернуть октеты (байты)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uidHex.isNotBlank()) {
                                val reversed = HexUtils.reverseOctets(uidHex, ":")
                                if (reverseOctets) {
                                    "Отправляется: $reversed"
                                } else {
                                    "Если включено: $reversed"
                                }
                            } else {
                                "Обратный порядок байт (Little/Big-Endian) для домофонов"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (uidHex.isNotBlank()) FontFamily.Monospace else FontFamily.Default,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = reverseOctets,
                        onCheckedChange = { reverseOctets = it }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // MIFARE / SAK profile selector (critical for Beward/intercom readers)
            Text(
                text = "Профиль карты (SAK / ATQA для домофона)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MIFARE_PROFILES.forEach { profile ->
                    FilterChip(
                        selected = sakHex.equals(profile.sakHex, ignoreCase = true),
                        onClick = {
                            sakHex = profile.sakHex
                            atqaHex = profile.atqaHex
                        },
                        label = { Text(profile.label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Иконка",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                KeyPresets.icons.forEach { preset ->
                    val selected = iconId == preset.id
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { iconId = preset.id },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = preset.icon,
                            contentDescription = preset.label,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Цвет карточки",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                KeyPresets.colors.forEach { colorPreset ->
                    val selected = colorId == colorPreset.id
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colorPreset.brush())
                            .then(
                                if (selected) Modifier.border(2.5.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .clickable { colorId = colorPreset.id },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Режим эмуляции HCE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = emulationMode == EmulationMode.NDEF_TYPE4,
                    onClick = { emulationMode = EmulationMode.NDEF_TYPE4 },
                    label = { Text("NDEF-метка (Type 4)") }
                )
                FilterChip(
                    selected = emulationMode == EmulationMode.CUSTOM_AID,
                    onClick = { emulationMode = EmulationMode.CUSTOM_AID },
                    label = { Text("Свой AID / СКУД") }
                )
            }

            Spacer(Modifier.height(10.dp))

            if (emulationMode == EmulationMode.NDEF_TYPE4) {
                OutlinedTextField(
                    value = ndefDisplayText,
                    onValueChange = {
                        ndefDisplayText = it
                        ndefRawHex = ""
                    },
                    label = { Text("Содержимое NDEF (текст или код)") },
                    placeholder = { Text("Заполняется автоматически при сканировании") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            } else {
                OutlinedTextField(
                    value = customAidHex,
                    onValueChange = { customAidHex = it.uppercase() },
                    label = { Text("AID (HEX, 10–32 символа)") },
                    placeholder = { Text("F0010203040506") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (editingKey != null && onDelete != null) {
                    OutlinedButton(
                        onClick = { onDelete(editingKey.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }

                Button(
                    onClick = {
                        val finalName = name.trim().ifBlank { "NFC Ключ" }
                        val key = NfcKey(
                            id = editingKey?.id ?: java.util.UUID.randomUUID().toString(),
                            name = finalName,
                            subtitle = subtitle.trim(),
                            iconId = iconId,
                            colorId = colorId,
                            uidHex = uidHex.trim(),
                            reverseOctets = reverseOctets,
                            atqaHex = atqaHex.trim().ifBlank { "0400" },
                            sakHex = sakHex.trim().ifBlank { "08" },
                            techList = techList,
                            emulationMode = emulationMode,
                            customAidHex = customAidHex.trim().ifBlank { "F0010203040506" },
                            ndefRawHex = ndefRawHex,
                            ndefDisplayText = ndefDisplayText.trim(),
                            isHardwareUidOnlyWarning = isHardwareUidOnlyWarning,
                            createdAt = editingKey?.createdAt ?: System.currentTimeMillis(),
                            lastUsedAt = editingKey?.lastUsedAt ?: 0L
                        )
                        onSave(key)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (editingKey == null) "Сохранить ключ" else "Применить изменения",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
