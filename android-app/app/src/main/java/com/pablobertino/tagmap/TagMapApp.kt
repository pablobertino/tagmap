package com.pablobertino.tagmap

import android.app.Application
import com.pablobertino.tagmap.data.AppContainer
import com.pablobertino.tagmap.push.Notifications
import org.maplibre.android.MapLibre

class TagMapApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        MapLibre.getInstance(this)
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}
