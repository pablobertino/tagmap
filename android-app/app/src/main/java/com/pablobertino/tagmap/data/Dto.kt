package com.pablobertino.tagmap.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/** Fila de la vista `tagmap.app_trackers` (ver docs/CONTRATO-DATOS.md). */
@Serializable
data class AppTracker(
    val id: String,
    val name: String,
    val icon: String = "tag",
    val kind: String = "tag",             // tag | phone | other
    val color: String = "#1E88E5",
    val enabled: Boolean = true,
    @SerialName("supports_sound") val supportsSound: Boolean = false,
    @SerialName("last_location_id") val lastLocationId: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("accuracy_m") val accuracyM: Float? = null,
    val quality: String? = null,          // good | medium | low
    val source: String? = null,           // find_hub_network | own_device | unknown
    @SerialName("last_observed_at") val lastObservedAt: String? = null,
    @SerialName("last_received_at") val lastReceivedAt: String? = null,
    @SerialName("age_minutes") val ageMinutes: Int? = null,
    @SerialName("at_places") val atPlaces: String? = null,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val observedAt: OffsetDateTime? get() = lastObservedAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
}

/** Fila devuelta por la RPC `tagmap.app_locations`. */
@Serializable
data class LocationPoint(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    @SerialName("accuracy_m") val accuracyM: Float? = null,
    val quality: String? = null,
    val source: String? = null,
    @SerialName("observed_at") val observedAt: String,
) {
    val observed: OffsetDateTime get() = OffsetDateTime.parse(observedAt)
}

@Serializable
data class CollectorStatus(
    val id: String,
    val status: String,
    val message: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

/** Lugar favorito / geocerca (`tagmap.places`). */
@Serializable
data class Place(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    val name: String,
    val icon: String = "place",
    val color: String = "#43A047",
    val latitude: Double,
    val longitude: Double,
    @SerialName("radius_m") val radiusM: Int = 200,
)

/** Regla tag ↔ lugar (`tagmap.geofence_rules`). */
@Serializable
data class GeofenceRule(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    @SerialName("tracker_id") val trackerId: String,
    @SerialName("place_id") val placeId: String,
    val enabled: Boolean = true,
    @SerialName("notify_entry") val notifyEntry: Boolean = true,
    @SerialName("notify_exit") val notifyExit: Boolean = true,
    @SerialName("confirmation_minutes") val confirmationMinutes: Int = 10,
    @SerialName("cooldown_minutes") val cooldownMinutes: Int = 30,
    @SerialName("muted_until") val mutedUntil: String? = null,
    @SerialName("current_state") val currentState: String = "UNKNOWN",
)

/** Evento de llegada/salida (`tagmap.app_events`). */
@Serializable
data class AppEvent(
    val id: String,
    @SerialName("tracker_id") val trackerId: String,
    @SerialName("place_id") val placeId: String,
    @SerialName("event_type") val eventType: String,     // ENTRY | EXIT
    @SerialName("observed_at") val observedAt: String,
    @SerialName("created_at") val createdAt: String,
    val status: String,                                  // CREATED | SENT | READ | SUPPRESSED
    @SerialName("suppress_reason") val suppressReason: String? = null,
    @SerialName("distance_m") val distanceM: Float? = null,
    @SerialName("tracker_name") val trackerName: String,
    @SerialName("place_name") val placeName: String,
) {
    val observed: OffsetDateTime get() = OffsetDateTime.parse(observedAt)
    val isEntry: Boolean get() = eventType == "ENTRY"
    val unread: Boolean get() = status == "CREATED" || status == "SENT"
}

/** Pedido a ejecutar por el recolector (`tagmap.action_requests`). */
@Serializable
data class ActionRequest(
    val id: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    @SerialName("tracker_id") val trackerId: String,
    val action: String,                    // sound_start | sound_stop | refresh
    val status: String = "pending",        // pending | running | done | failed
    val result: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)
