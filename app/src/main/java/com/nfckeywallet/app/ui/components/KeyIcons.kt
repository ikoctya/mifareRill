package com.nfckeywallet.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DoorFront
import androidx.compose.material.icons.rounded.Elevator
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Warehouse
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class KeyIconOption(
    val id: String,
    val label: String,
    val icon: ImageVector
)

data class KeyColorOption(
    val id: String,
    val label: String,
    val start: Color,
    val end: Color
) {
    fun brush(): Brush = Brush.linearGradient(listOf(start, end))
}

object KeyPresets {
    val icons = listOf(
        KeyIconOption("key", "Ключ", Icons.Rounded.Key),
        KeyIconOption("home", "Дом", Icons.Rounded.Home),
        KeyIconOption("office", "Офис", Icons.Rounded.Apartment),
        KeyIconOption("badge", "Пропуск", Icons.Rounded.Badge),
        KeyIconOption("car", "Шлагбаум", Icons.Rounded.DirectionsCar),
        KeyIconOption("garage", "Гараж", Icons.Rounded.Garage),
        KeyIconOption("door", "Домофон", Icons.Rounded.DoorFront),
        KeyIconOption("gym", "Спортзал", Icons.Rounded.FitnessCenter),
        KeyIconOption("lock", "Замок", Icons.Rounded.Lock),
        KeyIconOption("work", "Работа", Icons.Rounded.Work),
        KeyIconOption("elevator", "Лифт", Icons.Rounded.Elevator),
        KeyIconOption("hotel", "Отель", Icons.Rounded.Hotel),
        KeyIconOption("metro", "Проездной", Icons.Rounded.Subway),
        KeyIconOption("card", "Карта", Icons.Rounded.CreditCard),
        KeyIconOption("warehouse", "Склад", Icons.Rounded.Warehouse),
        KeyIconOption("vpnkey", "СКУД", Icons.Rounded.VpnKey)
    )

    val colors = listOf(
        KeyColorOption("indigo", "Индиго", Color(0xFF3949AB), Color(0xFF1A237E)),
        KeyColorOption("emerald", "Изумруд", Color(0xFF10B981), Color(0xFF047857)),
        KeyColorOption("cyan", "Неон", Color(0xFF06B6D4), Color(0xFF0369A1)),
        KeyColorOption("violet", "Фиолет", Color(0xFF8B5CF6), Color(0xFF5B21B6)),
        KeyColorOption("rose", "Рубин", Color(0xFFF43F5E), Color(0xFF9F1239)),
        KeyColorOption("amber", "Янтарь", Color(0xFFF59E0B), Color(0xFFB45309)),
        KeyColorOption("sunset", "Закат", Color(0xFFFF6B6B), Color(0xFFC026D3)),
        KeyColorOption("slate", "Графит", Color(0xFF475569), Color(0xFF1E293B))
    )

    fun iconFor(id: String): ImageVector =
        icons.firstOrNull { it.id == id }?.icon ?: Icons.Rounded.Key

    fun colorFor(id: String): KeyColorOption =
        colors.firstOrNull { it.id == id } ?: colors.first()
}
