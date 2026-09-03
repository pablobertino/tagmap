package com.pablobertino.tagmap.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Íconos elegibles por tag (columna trackers.icon) + emoji para la etiqueta del mapa. */
enum class TagIcon(val id: String, val label: String, val vector: ImageVector, val emoji: String) {
    TAG("tag", "Tag", Icons.Default.Sell, "🏷️"),
    PHONE("phone", "Teléfono", Icons.Default.Smartphone, "📱"),
    PERSON("person", "Persona", Icons.Default.Person, "🧑"),
    KEYS("keys", "Llaves", Icons.Default.Key, "🔑"),
    BAG("bag", "Bolso / mochila", Icons.Default.Work, "🎒"),
    LUGGAGE("luggage", "Valija", Icons.Default.Luggage, "🧳"),
    CAR("car", "Auto", Icons.Default.DirectionsCar, "🚗"),
    BIKE("bike", "Bici / moto", Icons.AutoMirrored.Filled.DirectionsBike, "🚲"),
    PET("pet", "Mascota", Icons.Default.Pets, "🐾");

    companion object {
        /** El ícono elegido por el usuario; si no eligió, uno según el tipo de dispositivo. */
        fun resolve(icon: String?, kind: String?): TagIcon =
            entries.firstOrNull { it.id == icon && icon != "tag" }
                ?: when (kind) { "phone" -> PHONE; else -> TAG }
    }
}
