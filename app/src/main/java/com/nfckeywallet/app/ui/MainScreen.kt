package com.nfckeywallet.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfckeywallet.app.R
import com.nfckeywallet.app.model.AppSettings
import com.nfckeywallet.app.model.NfcKey
import com.nfckeywallet.app.nfc.UidBackendType
import com.nfckeywallet.app.nfc.UidEmulationStatus
import com.nfckeywallet.app.ui.components.KeyPresets
import com.nfckeywallet.app.ui.theme.StealthColors

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
    returnWalletLabel: String,
    remainingSeconds: Int,
    settings: AppSettings,
    lastTapMessage: String?,
    onSelectKey: (NfcKey) -> Unit,
    onEditKey: (NfcKey) -> Unit,
    onAddKeyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOpenPermissionSetup: () -> Unit,
    onActivatePriority: () -> Unit,
    onReleasePriority: () -> Unit,
    onExtendTimer: () -> Unit,
    onOpenNfcSettings: () -> Unit
) {
    val backendBadge = when {
        uidStatus.active && uidStatus.backendUsed == UidBackendType.RAW_NCI -> "RAW NCI"
        uidStatus.active && uidStatus.backendUsed == UidBackendType.OEM_EXTENSION -> "OEM API"
        uidStatus.active && uidStatus.backendUsed == UidBackendType.ROOT_SU -> "ROOT UID"
        uidStatus.active -> "UID OK"
        else -> "HCE"
    }

    Scaffold(
        containerColor = StealthColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_brand_logo),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "mifareRill",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = StealthColors.TextPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            color = if (uidStatus.active) StealthColors.EmeraldSurface else StealthColors.SurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (uidStatus.active) StealthColors.EmeraldBorder else StealthColors.Border
                            )
                        ) {
                            Text(
                                text = backendBadge,
                                color = if (uidStatus.active) StealthColors.EmeraldBright else StealthColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Настройки",
                            tint = StealthColors.TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StealthColors.Background
                )
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isNfcEnabled) {
                item(span = { GridItemSpan(2) }) {
                    NfcDisabledBanner(onOpenNfcSettings = onOpenNfcSettings)
                }
            }

            if (!hasWriteSecureSettings) {
                item(span = { GridItemSpan(2) }) {
                    PermissionPromptStrip(onClick = onOpenPermissionSetup)
                }
            }

            if (lastTapMessage != null) {
                item(span = { GridItemSpan(2) }) {
                    Surface(
                        color = StealthColors.EmeraldSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, StealthColors.EmeraldBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = StealthColors.EmeraldBright,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = lastTapMessage,
                                color = StealthColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (selectedKey != null) {
                // Unified A3 Cockpit Hero Card (colored with user's chosen key color)
                item(span = { GridItemSpan(2) }) {
                    UnifiedCockpitHeroCard(
                        key = selectedKey,
                        isPriorityActive = isPriorityActive,
                        isSystemDefaultSwitched = isSystemDefaultSwitched,
                        uidActive = uidStatus.active,
                        returnWalletLabel = returnWalletLabel,
                        remainingSeconds = remainingSeconds,
                        totalSeconds = settings.autoReleaseSeconds,
                        onEdit = { onEditKey(selectedKey) },
                        onTapCard = {
                            if (!isPriorityActive) onActivatePriority() else onExtendTimer()
                        }
                    )
                }

                // Equal-height symmetrical action pills directly below hero card
                item(span = { GridItemSpan(2) }) {
                    CockpitActionRow(
                        isPriorityActive = isPriorityActive,
                        autoReleaseSeconds = settings.autoReleaseSeconds,
                        onActivate = onActivatePriority,
                        onExtend = onExtendTimer,
                        onRelease = onReleasePriority
                    )
                }
            } else {
                item(span = { GridItemSpan(2) }) {
                    EmptyStateCard(onAddKeyClick = onAddKeyClick)
                }
            }

            // 2-Column Stealth Key Grid (each tile tinted with its chosen key color) + "+ Добавить"
            items(keys, key = { it.id }) { keyItem ->
                StealthKeyTile(
                    key = keyItem,
                    isSelected = keyItem.id == selectedKey?.id && isPriorityActive,
                    onClick = { onSelectKey(keyItem) },
                    onEdit = { onEditKey(keyItem) }
                )
            }

            item {
                AddKeyGridTile(onClick = onAddKeyClick)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnifiedCockpitHeroCard(
    key: NfcKey,
    isPriorityActive: Boolean,
    isSystemDefaultSwitched: Boolean,
    uidActive: Boolean,
    returnWalletLabel: String,
    remainingSeconds: Int,
    totalSeconds: Int,
    onEdit: () -> Unit,
    onTapCard: () -> Unit
) {
    val colorPreset = KeyPresets.colorFor(key.colorId)
    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    val isLive = isPriorityActive && (isSystemDefaultSwitched || uidActive)
    val statusDotColor = if (isLive) StealthColors.EmeraldBright else if (isPriorityActive) StealthColors.Amber else StealthColors.CrimsonBright

    val statusText = when {
        isPriorityActive && totalSeconds > 0 && remainingSeconds > 0 -> "В ЭФИРЕ • ${remainingSeconds}с"
        isPriorityActive -> "В ЭФИРЕ"
        else -> "ОЖИДАНИЕ"
    }

    val progress = when {
        !isPriorityActive -> 0f
        totalSeconds <= 0 -> 1f
        else -> (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    }

    val cardTintBrush = Brush.linearGradient(
        colors = listOf(
            colorPreset.start.copy(alpha = if (isPriorityActive) 0.30f else 0.18f),
            colorPreset.end.copy(alpha = if (isPriorityActive) 0.14f else 0.08f),
            StealthColors.Surface
        )
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { onTapCard() },
        shape = RoundedCornerShape(22.dp),
        color = StealthColors.Surface,
        border = BorderStroke(
            width = 1.2.dp,
            color = colorPreset.start.copy(alpha = if (isPriorityActive) 0.78f else 0.42f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardTintBrush)
        ) {
            // Subtle radar glow rings tinted in the key's chosen color
            Canvas(modifier = Modifier.matchParentSize()) {
                val center = Offset(size.width * 0.74f, size.height * 0.46f)
                val baseRadius = size.minDimension * 0.22f
                val glowColor = colorPreset.start

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = if (isPriorityActive) 0.28f else 0.12f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 0.85f
                    ),
                    center = center,
                    radius = size.minDimension * 0.85f
                )

                for (i in 0..3) {
                    val fraction = ((pulsePhase + i * 0.25f) % 1f)
                    val radius = baseRadius + fraction * size.minDimension * 0.62f
                    val alpha = if (isPriorityActive) (1f - fraction) * 0.36f else 0.10f
                    drawCircle(
                        color = glowColor.copy(alpha = alpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 16.dp)
                ) {
                    // Top status row: pulsing dot + status + edit button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                color = StealthColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Редактировать",
                                tint = StealthColors.TextPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Key icon badge + title + Monospace UID
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(colorPreset.brush()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = KeyPresets.iconFor(key.iconId),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = key.name,
                                color = StealthColors.TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val displayUid = key.effectiveUidHex()
                            if (displayUid.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "UID $displayUid",
                                    color = StealthColors.TextPrimary.copy(alpha = 0.72f),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Crisp informative micro-chips row
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MicroInfoPill("SAK ${key.safeSakHex()}")
                        MicroInfoPill(if (key.reverseOctets) "Байты ⇄" else "Прямой UID")
                        val cleanReturn = returnWalletLabel.takeIf {
                            it.isNotBlank() && it != "Не определено"
                        } ?: "Кошелёк"
                        MicroInfoPill("$cleanReturn → авто")
                    }
                }

                // Sleek progress line along bottom edge of card in key's color
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp),
                    color = colorPreset.start,
                    trackColor = StealthColors.BorderSubtle
                )
            }
        }
    }
}

@Composable
private fun MicroInfoPill(text: String) {
    Surface(
        color = Color(0xFF0E131A).copy(alpha = 0.82f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Text(
            text = text,
            color = StealthColors.TextPrimary.copy(alpha = 0.90f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun CockpitActionRow(
    isPriorityActive: Boolean,
    autoReleaseSeconds: Int,
    onActivate: () -> Unit,
    onExtend: () -> Unit,
    onRelease: () -> Unit
) {
    val extendLabel = if (autoReleaseSeconds > 0) "+${autoReleaseSeconds} сек" else "Продлить"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isPriorityActive) {
            Button(
                onClick = onExtend,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, StealthColors.EmeraldBorder),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StealthColors.EmeraldSurface,
                    contentColor = StealthColors.EmeraldBright
                )
            ) {
                Text(
                    text = extendLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onRelease,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, StealthColors.CrimsonBorder),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StealthColors.CrimsonSurface,
                    contentColor = StealthColors.CrimsonBright
                )
            ) {
                Text(
                    text = "Вернуть оплату",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Button(
                onClick = onActivate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StealthColors.Emerald,
                    contentColor = Color(0xFF031F16)
                )
            ) {
                Text(
                    text = "Активировать ключ в эфире",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StealthKeyTile(
    key: NfcKey,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val colorPreset = KeyPresets.colorFor(key.colorId)
    val tileTintBrush = Brush.linearGradient(
        colors = listOf(
            colorPreset.start.copy(alpha = if (isSelected) 0.34f else 0.17f),
            colorPreset.end.copy(alpha = if (isSelected) 0.14f else 0.06f),
            StealthColors.Surface
        )
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(114.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = StealthColors.Surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = colorPreset.start.copy(alpha = if (isSelected) 0.90f else 0.42f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tileTintBrush)
                .padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colorPreset.brush()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = KeyPresets.iconFor(key.iconId),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Изменить",
                        tint = StealthColors.TextPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Column {
                Text(
                    text = key.name,
                    color = StealthColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = key.subtitle.ifBlank { "SAK ${key.safeSakHex()}" },
                    color = if (isSelected) colorPreset.start else StealthColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AddKeyGridTile(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(114.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = StealthColors.Surface.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, StealthColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = StealthColors.CrimsonBright,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Добавить",
                color = StealthColors.TextSecondary,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun PermissionPromptStrip(onClick: () -> Unit) {
    Surface(
        color = StealthColors.CrimsonSurface.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StealthColors.CrimsonBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = StealthColors.CrimsonBright,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Выдать системное право без ПК (1 шаг) →",
                color = StealthColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun NfcDisabledBanner(onOpenNfcSettings: () -> Unit) {
    Surface(
        color = StealthColors.CrimsonSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StealthColors.CrimsonBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenNfcSettings() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = StealthColors.CrimsonBright,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "NFC выключен — нажмите, чтобы включить",
                color = StealthColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyStateCard(onAddKeyClick: () -> Unit) {
    Surface(
        color = StealthColors.Surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, StealthColors.Border),
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
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(StealthColors.EmeraldSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Nfc,
                    contentDescription = null,
                    tint = StealthColors.EmeraldBright,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Нет сохранённых ключей",
                color = StealthColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Приложите домофонный ключ или карту к телефону, чтобы добавить первую запись.",
                color = StealthColors.TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAddKeyClick,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StealthColors.Emerald,
                    contentColor = Color(0xFF031F16)
                )
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Сканировать NFC-ключ", fontWeight = FontWeight.Bold)
            }
        }
    }
}
