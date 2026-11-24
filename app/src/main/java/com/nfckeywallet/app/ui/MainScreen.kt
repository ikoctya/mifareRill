package com.nfckeywallet.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfckeywallet.app.model.AppSettings
import com.nfckeywallet.app.model.EmulationMode
import com.nfckeywallet.app.model.NfcKey
import com.nfckeywallet.app.nfc.UidEmulationStatus
import com.nfckeywallet.app.ui.components.KeyPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    keys: List<NfcKey>,
    selectedKey: NfcKey?,
    isNfcEnabled: Boolean,
    isPriorityActive: Boolean,
    isSystemDefaultSwitched: Boolean,
    hasWriteSecureSettings: Boolean,
    uidStatus: UidEmulationStatus,
    remainingSeconds: Int,
    settings: AppSettings,
    lastTapMessage: String?,
    onSelectKey: (NfcKey) -> Unit,
    onEditKey: (NfcKey) -> Unit,
    onAddKeyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onActivatePriority: () -> Unit,
    onReleasePriority: () -> Unit,
    onExtendTimer: () -> Unit,
    onOpenNfcSettings: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Nfc,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "NFC Ключница",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Настройки"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddKeyClick,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Добавить ключ", fontWeight = FontWeight.SemiBold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isNfcEnabled) {
                item(span = { GridItemSpan(2) }) {
                    NfcDisabledBanner(onOpenNfcSettings = onOpenNfcSettings)
                }
            }

            item(span = { GridItemSpan(2) }) {
                PriorityStatusCard(
                    isPriorityActive = isPriorityActive,
                    isSystemDefaultSwitched = isSystemDefaultSwitched,
                    hasWriteSecureSettings = hasWriteSecureSettings,
                    uidStatus = uidStatus,
                    remainingSeconds = remainingSeconds,
                    totalSeconds = settings.autoReleaseSeconds,
                    hasSelectedKey = selectedKey != null,
                    onActivate = onActivatePriority,
                    onRelease = onReleasePriority,
                    onExtend = onExtendTimer,
                    onOpenSettings = onSettingsClick
                )
            }

            if (lastTapMessage != null) {
                item(span = { GridItemSpan(2) }) {
                    Surface(
                        color = Color(0xFF0E4D36),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF5CE1B8),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = lastTapMessage,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (selectedKey != null) {
                item(span = { GridItemSpan(2) }) {
                    ActiveKeyHeroCard(
                        key = selectedKey,
                        isPriorityActive = isPriorityActive,
                        onEdit = { onEditKey(selectedKey) },
                        onTapCard = {
                            if (!isPriorityActive) onActivatePriority() else onExtendTimer()
                        }
                    )
                }
            } else {
                item(span = { GridItemSpan(2) }) {
                    EmptyStateCard(onAddKeyClick = onAddKeyClick)
                }
            }

            if (keys.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Мои ключи (${keys.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Нажмите для выбора",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(keys, key = { it.id }) { keyItem ->
                    KeyTileCard(
                        key = keyItem,
                        isSelected = keyItem.id == selectedKey?.id,
                        onClick = { onSelectKey(keyItem) },
                        onEdit = { onEditKey(keyItem) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NfcDisabledBanner(onOpenNfcSettings: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NFC выключен",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Включите NFC в настройках, чтобы считывать и предъявлять ключи",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onOpenNfcSettings) {
                Text("Включить")
            }
        }
    }
}

@Composable
private fun PriorityStatusCard(
    isPriorityActive: Boolean,
    isSystemDefaultSwitched: Boolean,
    hasWriteSecureSettings: Boolean,
    uidStatus: UidEmulationStatus,
    remainingSeconds: Int,
    totalSeconds: Int,
    hasSelectedKey: Boolean,
    onActivate: () -> Unit,
    onRelease: () -> Unit,
    onExtend: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isFullyActive = isSystemDefaultSwitched || uidStatus.active
    val activeAccent = if (isFullyActive) Color(0xFF5CE1B8) else Color(0xFFFFB77C)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPriorityActive) {
                activeAccent.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPriorityActive) {
                                activeAccent.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPriorityActive) Icons.Rounded.Contactless else Icons.Rounded.Payments,
                        contentDescription = null,
                        tint = if (isPriorityActive) activeAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isPriorityActive && isFullyActive ->
                                "NFC Ключница активна"
                            isPriorityActive ->
                                "Приоритет только пока открыт экран"
                            else ->
                                "Возвращено ваше приложение оплаты"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = when {
                            isPriorityActive && isFullyActive && totalSeconds > 0 && remainingSeconds > 0 ->
                                "Подносите к считывателю • возврат оплаты через ${remainingSeconds}с"
                            isPriorityActive && isFullyActive ->
                                "Подносите телефон к считывателю"
                            isPriorityActive && hasWriteSecureSettings && totalSeconds > 0 && remainingSeconds > 0 ->
                                "Приоритет переднего плана активен. Осталось ${remainingSeconds}с"
                            isPriorityActive && totalSeconds > 0 && remainingSeconds > 0 ->
                                "В настройках Android не меняется (нет ADB-права). Осталось ${remainingSeconds}с"
                            isPriorityActive ->
                                "В настройках Android не меняется без ADB-разрешения"
                            else ->
                                "Системное приложение оплаты активно в обычном режиме"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isPriorityActive && uidStatus.detailMessage.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = if (uidStatus.active) {
                        Color(0xFF10B981).copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSettings() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (uidStatus.active) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (uidStatus.active) Color(0xFF5CE1B8) else Color(0xFFFFB77C),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uidStatus.detailMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!hasWriteSecureSettings) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSettings() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = Color(0xFFFFB77C),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Чтобы реально менять «Оплата по умолчанию» в системе, выдайте 1 раз ADB-право (Настройки ⚙)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isPriorityActive && totalSeconds > 0) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    val progress = if (totalSeconds > 0) {
                        (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
                    } else 1f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = activeAccent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isPriorityActive) {
                    FilledTonalButton(
                        onClick = onExtend,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Продлить")
                    }
                    Button(
                        onClick = onRelease,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Вернуть оплату")
                    }
                } else {
                    Button(
                        onClick = onActivate,
                        enabled = hasSelectedKey,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Contactless, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Активировать ключ для поднесения")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveKeyHeroCard(
    key: NfcKey,
    isPriorityActive: Boolean,
    onEdit: () -> Unit,
    onTapCard: () -> Unit
) {
    val colorPreset = KeyPresets.colorFor(key.colorId)
    val infiniteTransition = rememberInfiniteTransition(label = "nfcPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(206.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable { onTapCard() },
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorPreset.brush())
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = KeyPresets.iconFor(key.iconId),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPriorityActive) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .scale(pulseScale)
                                    .alpha(pulseAlpha)
                                    .clip(CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Contactless,
                                contentDescription = "Готов к NFC",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Редактировать",
                            tint = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                Text(
                    text = key.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (key.subtitle.isNotBlank()) {
                    Text(
                        text = key.subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modeBadge = when (key.emulationMode) {
                        EmulationMode.NDEF_TYPE4 -> "NDEF Type 4"
                        EmulationMode.CUSTOM_AID -> "AID ${key.customAidHex.take(10)}"
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = modeBadge,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    val displayUid = key.effectiveUidHex()
                    if (displayUid.isNotBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (key.reverseOctets) "UID $displayUid ⇄" else "UID $displayUid",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyTileCard(
    key: NfcKey,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val colorPreset = KeyPresets.colorFor(key.colorId)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colorPreset.start else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorPreset.brush()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = KeyPresets.iconFor(key.iconId),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Изменить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column {
                Text(
                    text = key.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = key.subtitle.ifBlank {
                        if (isSelected) "Выбран сейчас" else "Нажмите для активации"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colorPreset.start else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(onAddKeyClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Nfc,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Нет добавленных ключей",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Приложите NFC-метку или пропуск к телефону, чтобы добавить первый ключ с собственной иконкой и названием.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAddKeyClick,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Сканировать NFC-ключ")
            }
        }
    }
}
