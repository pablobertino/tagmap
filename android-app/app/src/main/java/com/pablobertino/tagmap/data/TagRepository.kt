package com.pablobertino.tagmap.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TagRepository(private val client: SupabaseClient) {

    suspend fun trackers(): List<AppTracker> =
        client.postgrest.from("app_trackers")
            .select {
                order("name", Order.ASCENDING)
            }
            .decodeList()

    suspend fun tracker(id: String): AppTracker? =
        client.postgrest.from("app_trackers")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull()

    suspend fun locations(
        trackerId: String,
        from: OffsetDateTime,
        to: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
        accuracyMax: Float? = null,
        limit: Int = 2000,
    ): List<LocationPoint> {
        val params = buildJsonObject {
            put("p_tracker_id", trackerId)
            put("p_from", from.toString())
            put("p_to", to.toString())
            if (accuracyMax != null) put("p_accuracy_max", accuracyMax)
            put("p_limit", limit)
        }
        return client.postgrest.rpc("app_locations", params).decodeList()
    }

    suspend fun updateTracker(id: String, name: String, icon: String, color: String) {
        client.postgrest.from("trackers").update({
            set("name", name); set("icon", icon); set("color", color)
        }) {
            filter { eq("id", id) }
        }
    }

    suspend fun setTrackerEnabled(id: String, enabled: Boolean) {
        client.postgrest.from("trackers").update({ set("enabled", enabled) }) {
            filter { eq("id", id) }
        }
    }

    /** Pide al recolector una acción (hacer sonar, etc.). Devuelve el pedido creado. */
    suspend fun requestAction(trackerId: String, action: String): ActionRequest =
        client.postgrest.from("action_requests").insert(
            ActionRequest(ownerId = client.auth.currentUserOrNull()?.id ?: error("sin sesión"), trackerId = trackerId, action = action)
        ) { select() }.decodeSingle()

    suspend fun action(id: String): ActionRequest? =
        client.postgrest.from("action_requests").select { filter { eq("id", id) } }.decodeSingleOrNull()

    // ------------------------------------------------- compartir

    suspend fun shares(trackerId: String): List<TrackerShare> =
        client.postgrest.rpc("app_tracker_shares", buildJsonObject { put("p_tracker_id", trackerId) }).decodeList()

    suspend fun share(trackerId: String, email: String) {
        client.postgrest.rpc("app_share_tracker", buildJsonObject { put("p_tracker_id", trackerId); put("p_email", email) })
    }

    /** userId null = el invitado se quita a sí mismo. */
    suspend fun unshare(trackerId: String, userId: String? = null) {
        client.postgrest.rpc("app_unshare_tracker", buildJsonObject {
            put("p_tracker_id", trackerId); if (userId != null) put("p_user_id", userId)
        })
    }

    suspend fun sharedBy(): List<SharedBy> = client.postgrest.rpc("app_shared_by").decodeList()

    suspend fun collectors(): List<CollectorStatus> =
        client.postgrest.from("collectors").select().decodeList()
}
