package com.fogdetector

import android.app.Application
import com.fogdetector.notification.NotificationHelper

class FogDetectorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}
