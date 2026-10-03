package com.deniz0706.dose

import android.app.Application
import com.deniz0706.dose.reminder.NotificationHelper

class DoseApp : Application() {

    override fun onCreate() {
        super.onCreate()

        NotificationHelper.createNotificationChannel(this)
    }
}
