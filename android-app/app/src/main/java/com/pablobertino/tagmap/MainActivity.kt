package com.pablobertino.tagmap

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.pablobertino.tagmap.push.Notifications
import com.pablobertino.tagmap.push.TagMapMessagingService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.pablobertino.tagmap.ui.AppNav
import com.pablobertino.tagmap.ui.theme.TagMapTheme
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    /** true cuando la app se abrió desde el link de "recuperar contraseña". */
    private val recoveryMode = mutableStateOf(false)

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TagMapApp).container
        consumeDeeplink(intent, container)
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(this)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        TagMapMessagingService.syncToken(container)
        val lastCrash = CrashReporter.consume(this)
        setContent {
            TagMapTheme {
                Surface { AppNav(container, recoveryMode) }
                var crash by remember { mutableStateOf(lastCrash) }
                crash?.let { text ->
                    AlertDialog(
                        onDismissRequest = { crash = null },
                        title = { Text("La app se cerró por un error") },
                        text = {
                            Text(text, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                modifier = Modifier.verticalScroll(rememberScrollState()))
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("crash", text))
                                crash = null
                            }) { Text("Copiar y cerrar") }
                        },
                        dismissButton = { TextButton(onClick = { crash = null }) { Text("Cerrar") } },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeDeeplink(intent, (application as TagMapApp).container)
    }

    private fun consumeDeeplink(intent: Intent?, container: com.pablobertino.tagmap.data.AppContainer) {
        val data = intent?.data ?: return
        if (data.scheme != "tagmap") return
        if (data.toString().contains("type=recovery")) recoveryMode.value = true
        container.supabase.handleDeeplinks(intent)
    }
}
