package com.pablobertino.tagmap.data

import android.content.Context
import com.pablobertino.tagmap.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

/** Inyección de dependencias manual (sin Hilt para mantener el build simple). */
class AppContainer(context: Context) {
    val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        })
        install(Auth) {
            // tagmap://auth  → deep link de recuperación de clave y confirmación de email
            scheme = "tagmap"
            host = "auth"
        }
        install(Postgrest) {
            defaultSchema = BuildConfig.SUPABASE_SCHEMA
        }
    }

    val authRepository = AuthRepository(supabase)
    val tagRepository = TagRepository(supabase)
    val placesRepository = PlacesRepository(supabase)
    val prefs = Prefs(context)
}
