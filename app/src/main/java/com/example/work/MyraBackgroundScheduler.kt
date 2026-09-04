package com.example.work

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * WorkManager Background Task Scheduler for Myra.
 * Orchestrates periodic background jobs (intelligence check, memory optimization, device health).
 */
class MyraBackgroundScheduler(private val context: Context) {

    companion object {
        private const val TAG = "MyraBackgroundScheduler"
        const val PERIODIC_WORK_NAME = "myra_periodic_ai_orchestration"
        const val ONE_TIME_WORK_NAME = "myra_immediate_ai_orchestration"

        private const val PREFS_NAME = "myra_work_scheduler"
        private const val PREF_ENABLED = "scheduler_enabled"
        private const val PREF_INTERVAL_MINUTES = "scheduler_interval_minutes"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val workManager: WorkManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isSchedulerEnabled = MutableStateFlow(prefs.getBoolean(PREF_ENABLED, true))
    val isSchedulerEnabled: StateFlow<Boolean> = _isSchedulerEnabled.asStateFlow()

    private val _selectedIntervalMinutes = MutableStateFlow(prefs.getLong(PREF_INTERVAL_MINUTES, 60L))
    val selectedIntervalMinutes: StateFlow<Long> = _selectedIntervalMinutes.asStateFlow()

    private val _lastRunTimestamp = MutableStateFlow(prefs.getLong(MyraBackgroundOrchestrationWorker.PREF_LAST_RUN, 0L))
    val lastRunTimestamp: StateFlow<Long> = _lastRunTimestamp.asStateFlow()

    private val _lastRunSummary = MutableStateFlow(
        prefs.getString(MyraBackgroundOrchestrationWorker.PREF_LAST_SUMMARY, "No background orchestration job executed yet.") ?: ""
    )
    val lastRunSummary: StateFlow<String> = _lastRunSummary.asStateFlow()

    private val _workStatus = MutableStateFlow("IDLE")
    val workStatus: StateFlow<String> = _workStatus.asStateFlow()

    init {
        observeWorkManagerStatus()
    }

    private fun observeWorkManagerStatus() {
        try {
            workManager.getWorkInfosForUniqueWorkLiveData(PERIODIC_WORK_NAME)
                .observeForever { workInfos ->
                    val info = workInfos?.firstOrNull()
                    if (info != null) {
                        _workStatus.value = info.state.name
                    }
                    refreshLastRunInfo()
                }
        } catch (e: Exception) {
            Log.w(TAG, "Could not observe work manager status", e)
        }
    }

    fun refreshLastRunInfo() {
        _lastRunTimestamp.value = prefs.getLong(MyraBackgroundOrchestrationWorker.PREF_LAST_RUN, 0L)
        _lastRunSummary.value = prefs.getString(
            MyraBackgroundOrchestrationWorker.PREF_LAST_SUMMARY,
            "No background orchestration job executed yet."
        ) ?: ""
    }

    /**
     * Schedules periodic AI orchestration jobs via WorkManager.
     */
    fun schedulePeriodicOrchestration(
        intervalMinutes: Long = _selectedIntervalMinutes.value,
        requireNetwork: Boolean = true,
        requireBatteryNotLow: Boolean = true
    ) {
        val validInterval = intervalMinutes.coerceAtLeast(15L) // WorkManager periodic minimum is 15 mins
        prefs.edit()
            .putBoolean(PREF_ENABLED, true)
            .putLong(PREF_INTERVAL_MINUTES, validInterval)
            .apply()

        _isSchedulerEnabled.value = true
        _selectedIntervalMinutes.value = validInterval

        val constraintsBuilder = Constraints.Builder()
        if (requireNetwork) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }
        if (requireBatteryNotLow) {
            constraintsBuilder.setRequiresBatteryNotLow(true)
        }

        val workRequest = PeriodicWorkRequestBuilder<MyraBackgroundOrchestrationWorker>(
            validInterval,
            TimeUnit.MINUTES,
            5,
            TimeUnit.MINUTES
        )
            .setConstraints(constraintsBuilder.build())
            .addTag("myra_ai_orchestration")
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.i(TAG, "Scheduled periodic background orchestration every $validInterval minutes.")
    }

    /**
     * Cancels active periodic background work.
     */
    fun cancelPeriodicOrchestration() {
        prefs.edit().putBoolean(PREF_ENABLED, false).apply()
        _isSchedulerEnabled.value = false
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        _workStatus.value = "CANCELLED"
        Log.i(TAG, "Cancelled periodic background orchestration.")
    }

    /**
     * Triggers an immediate one-time background orchestration job.
     */
    fun runImmediateOrchestration() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<MyraBackgroundOrchestrationWorker>()
            .setConstraints(constraints)
            .addTag("myra_immediate_orchestration")
            .build()

        workManager.enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        _workStatus.value = "ENQUEUED"
        Log.i(TAG, "Triggered immediate background orchestration job.")
    }
}
