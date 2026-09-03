package com.pablobertino.tagmap.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pablobertino.tagmap.data.AuthRepository
import com.pablobertino.tagmap.ui.friendly
import kotlinx.coroutines.launch

/** Se muestra al abrir el link de "recuperar contraseña" del correo. */
@Composable
fun NewPasswordScreen(auth: AuthRepository, onDone: () -> Unit) {
    var p1 by rememberSaveable { mutableStateOf("") }
    var p2 by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nueva contraseña", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        PasswordField(p1, { p1 = it }, show, { show = !show })
        Spacer(Modifier.height(12.dp))
        PasswordField(p2, { p2 = it }, show, { show = !show }, label = "Repetir contraseña")
        Spacer(Modifier.height(20.dp))
        if (loading) CircularProgressIndicator() else Button(
            onClick = {
                when {
                    p1.length < 6 -> error = "Mínimo 6 caracteres"
                    p1 != p2 -> error = "Las contraseñas no coinciden"
                    else -> {
                        loading = true; error = null
                        scope.launch {
                            runCatching { auth.updatePassword(p1) }
                                .onSuccess { onDone() }
                                .onFailure { error = friendly(it); loading = false }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar") }
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
