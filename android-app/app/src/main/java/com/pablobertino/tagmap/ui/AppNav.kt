package com.pablobertino.tagmap.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pablobertino.tagmap.data.AppContainer
import com.pablobertino.tagmap.ui.detail.TrackerDetailScreen
import com.pablobertino.tagmap.ui.events.EventsScreen
import com.pablobertino.tagmap.ui.places.PlacesScreen
import com.pablobertino.tagmap.ui.login.LoginScreen
import com.pablobertino.tagmap.ui.login.NewPasswordScreen
import com.pablobertino.tagmap.ui.map.MapScreen
import com.pablobertino.tagmap.ui.trackers.TrackerListScreen
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun AppNav(container: AppContainer, recoveryMode: MutableState<Boolean>) {
    val session by container.authRepository.sessionStatus.collectAsStateWithLifecycle()

    when (session) {
        is SessionStatus.Initializing -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        is SessionStatus.Authenticated ->
            if (recoveryMode.value) NewPasswordScreen(container.authRepository) { recoveryMode.value = false }
            else MainNav()
        else -> LoginScreen()
    }
}

@Composable
private fun MainNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "trackers") {
        composable("trackers") {
            TrackerListScreen(
                onOpenMap = { nav.navigate("map") },
                onOpenTracker = { nav.navigate("tracker/$it") },
                onOpenPlaces = { nav.navigate("places") },
                onOpenEvents = { nav.navigate("events") },
            )
        }
        composable("map") {
            MapScreen(
                onBack = { nav.popBackStack() },
                onOpenTracker = { nav.navigate("tracker/$it") },
                onOpenPlaces = { nav.navigate("places") },
            )
        }
        composable("places") { PlacesScreen(onBack = { nav.popBackStack() }) }
        composable("events") { EventsScreen(onBack = { nav.popBackStack() }) }
        composable("tracker/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            TrackerDetailScreen(trackerId = entry.arguments!!.getString("id")!!, onBack = { nav.popBackStack() })
        }
    }
}
