package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.MyraDatabase
import com.example.services.MyraAssistantForegroundService

class MyraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Pre-warm database
        MyraDatabase.getDatabase(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MyraAssistantForegroundService.CHANNEL_ID,
                "Myra Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Myra Assistant ready for immediate commands"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
