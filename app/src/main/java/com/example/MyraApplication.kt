package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.MyraDatabase

class MyraApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "myra_assistant_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Myra Assistant Service"
        lateinit var instance: MyraApplication
            private set
    }

    lateinit var database: MyraDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = MyraDatabase.getDatabase(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent background service keeping Myra AI Assistant ready"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
