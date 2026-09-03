package com.pablobertino.tagmap.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.StateFlow

/** Deep link al que vuelven los correos de Supabase (recuperar clave / confirmar registro). */
const val AUTH_REDIRECT = "tagmap://auth"

class AuthRepository(private val client: SupabaseClient) {

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    val currentEmail: String? get() = client.auth.currentUserOrNull()?.email

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        ensureProfile()
    }

    /** Devuelve true si quedó logueado directo; false si hay que confirmar por email. */
    suspend fun signUp(email: String, password: String): Boolean {
        client.auth.signUpWith(Email, redirectUrl = AUTH_REDIRECT) {
            this.email = email.trim()
            this.password = password
        }
        val logged = client.auth.currentSessionOrNull() != null
        if (logged) ensureProfile()
        return logged
    }

    suspend fun sendPasswordReset(email: String) {
        client.auth.resetPasswordForEmail(email.trim(), redirectUrl = AUTH_REDIRECT)
    }

    suspend fun updatePassword(newPassword: String) {
        client.auth.updateUser { password = newPassword }
    }

    /** Crea el perfil en `tagmap.profiles` si no existe (no hay trigger sobre auth.users). */
    suspend fun ensureProfile() {
        runCatching { client.postgrest.rpc("ensure_profile") }
    }

    suspend fun signOut() = client.auth.signOut()
}
