package com.pablobertino.tagmap.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Lugares, reglas y eventos (spec §6.5, §6.6). */
class PlacesRepository(private val client: SupabaseClient) {

    private val uid: String get() = client.auth.currentUserOrNull()?.id ?: error("sin sesión")

    // ------------------------------------------------------------ lugares

    suspend fun places(): List<Place> =
        client.postgrest.from("places").select { order("name", Order.ASCENDING) }.decodeList()

    suspend fun createPlace(name: String, lat: Double, lon: Double, radiusM: Int, icon: String, color: String): Place =
        client.postgrest.from("places").insert(
            Place(ownerId = uid, name = name, latitude = lat, longitude = lon, radiusM = radiusM, icon = icon, color = color)
        ) { select() }.decodeSingle()

    suspend fun updatePlace(p: Place) {
        client.postgrest.from("places").update({
            set("name", p.name); set("radius_m", p.radiusM); set("icon", p.icon); set("color", p.color)
            set("latitude", p.latitude); set("longitude", p.longitude)
        }) { filter { eq("id", p.id) } }
    }

    /** Borra el lugar; las reglas asociadas caen en cascada (spec §15.10). */
    suspend fun deletePlace(id: String) {
        client.postgrest.from("places").delete { filter { eq("id", id) } }
    }

    // ------------------------------------------------------------- reglas

    suspend fun rules(): List<GeofenceRule> =
        client.postgrest.from("geofence_rules").select().decodeList()

    suspend fun rulesForPlace(placeId: String): List<GeofenceRule> =
        client.postgrest.from("geofence_rules").select { filter { eq("place_id", placeId) } }.decodeList()

    /** Crea o actualiza la regla tag↔lugar. Con ambos avisos apagados, la regla se borra. */
    suspend fun setRule(trackerId: String, placeId: String, notifyEntry: Boolean, notifyExit: Boolean) {
        val existing = client.postgrest.from("geofence_rules")
            .select { filter { eq("tracker_id", trackerId); eq("place_id", placeId) } }
            .decodeSingleOrNull<GeofenceRule>()
        when {
            !notifyEntry && !notifyExit -> existing?.let {
                client.postgrest.from("geofence_rules").delete { filter { eq("id", it.id) } }
            }
            existing == null -> client.postgrest.from("geofence_rules").insert(
                GeofenceRule(ownerId = uid, trackerId = trackerId, placeId = placeId,
                    notifyEntry = notifyEntry, notifyExit = notifyExit)
            )
            else -> client.postgrest.from("geofence_rules").update({
                set("notify_entry", notifyEntry); set("notify_exit", notifyExit); set("enabled", true)
            }) { filter { eq("id", existing.id) } }
        }
    }

    suspend fun muteRule(ruleId: String, minutes: Int) {
        client.postgrest.rpc("app_mute_rule", buildJsonObject { put("p_rule_id", ruleId); put("p_minutes", minutes) })
    }

    // ------------------------------------------------------------ eventos

    suspend fun events(limit: Int = 200): List<AppEvent> =
        client.postgrest.from("app_events").select {
            order("observed_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    suspend fun markRead(ids: List<String>) {
        if (ids.isEmpty()) return
        client.postgrest.rpc("app_mark_events_read", buildJsonObject {
            put("p_event_ids", kotlinx.serialization.json.JsonArray(ids.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        })
    }

    // ------------------------------------------------------------ alertas de sistema

    suspend fun alerts(limit: Int = 100): List<SystemAlert> =
        client.postgrest.from("system_alerts").select {
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList()

    suspend fun markAlertsRead(ids: List<String>) {
        if (ids.isEmpty()) return
        client.postgrest.rpc("app_mark_alerts_read", buildJsonObject {
            put("p_ids", kotlinx.serialization.json.JsonArray(ids.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        })
    }

    // ------------------------------------------------- dispositivo / push

    suspend fun registerDevice(fcmToken: String, deviceName: String) {
        client.postgrest.rpc("app_register_device", buildJsonObject {
            put("p_fcm_token", fcmToken); put("p_device_name", deviceName)
            put("p_tz", java.util.TimeZone.getDefault().id)   // la push se muestra en hora local del teléfono
        })
    }
}
