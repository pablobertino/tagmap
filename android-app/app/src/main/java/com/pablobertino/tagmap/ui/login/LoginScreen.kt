package com.pablobertino.tagmap.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pablobertino.tagmap.ui.AuthMode
import com.pablobertino.tagmap.ui.LoginViewModel

@Composable
fun LoginScreen(vm: LoginViewModel = viewModel(factory = LoginViewModel.Factory)) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf(vm.lastEmail) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TagMap", style = MaterialTheme.typography.headlineLarge)
        Text(
            when (ui.mode) {
                AuthMode.SIGN_IN -> "Tus tags de Find Hub, con historial"
                AuthMode.SIGN_UP -> "Crear cuenta"
                AuthMode.RESET -> "Recuperar contraseña"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("Email") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        if (ui.mode != AuthMode.RESET) {
            Spacer(Modifier.height(12.dp))
            PasswordField(password, { password = it }, showPassword, { showPassword = !showPassword })
        }
        Spacer(Modifier.height(20.dp))
        if (ui.loading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { vm.submit(email, password) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (ui.mode) {
                        AuthMode.SIGN_IN -> "Ingresar"
                        AuthMode.SIGN_UP -> "Registrarme"
                        AuthMode.RESET -> "Enviar correo"
                    }
                )
            }
        }
        ui.error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        ui.info?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            when (ui.mode) {
                AuthMode.SIGN_IN -> {
                    TextButton(onClick = { vm.setMode(AuthMode.RESET) }) { Text("Olvidé mi contraseña") }
                    TextButton(onClick = { vm.setMode(AuthMode.SIGN_UP) }) { Text("Crear cuenta") }
                }
                else -> TextButton(onClick = { vm.setMode(AuthMode.SIGN_IN) }) { Text("Volver a ingresar") }
            }
        }
    }
}

@Composable
fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    visible: Boolean,
    onToggle: () -> Unit,
    label: String = "Contraseña",
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Mostrar contraseña")
            }
        },
    )
}
