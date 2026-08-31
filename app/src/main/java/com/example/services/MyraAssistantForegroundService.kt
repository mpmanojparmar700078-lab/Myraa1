package com.example.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.MyraApplication
import com.example.R
import com.example.models.AssistantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MyraAssistantForegroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.myra.action.START"
        const val ACTION_STOP = "com.example.myra.action.STOP"
        const val ACTION_UPDATE_STATUS = "com.example.myra.action.UPDATE_STATUS"
        const val EXTRA_STATUS_TEXT = "extra_status_text"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, MyraAssistantForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MyraAssistantForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    inner class LocalBinder : Binder() {
        fun getService(): MyraAssistantForegroundService = this@MyraAssistantForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        _isServiceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                _isServiceRunning.value = false
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATUS -> {
                val status = intent.getStringExtra(EXTRA_STATUS_TEXT) ?: "Ready for commands"
                updateNotification(status)
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification("मायरा सक्रिय है (Myra Assistant is Ready)"))
                _isServiceRunning.value = true
            }
        }
        return START_STICKY
    }

    fun updateStatus(state: AssistantState, detail: String? = null) {
        val text = when (state) {
            AssistantState.IDLE -> "मायरा तैयार है (Ready for commands)"
            AssistantState.PROCESSING -> "सोच रही हूँ… (Analyzing intent)"
            AssistantState.EXECUTING_ACTION -> detail ?: "एक्शन चल रहा है (Executing action)"
            AssistantState.BACKGROUND_READY -> "बैकग्राउंड में तैयार (Active in background)"
            AssistantState.ERROR -> "त्रुटि (Error in action)"
        }
        updateNotification(text)
    }

    private fun updateNotification(statusText: String) {
        val notification = buildNotification(statusText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MyraApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Myra AI Assistant")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.myra_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        _isServiceRunning.value = false
    }
}
