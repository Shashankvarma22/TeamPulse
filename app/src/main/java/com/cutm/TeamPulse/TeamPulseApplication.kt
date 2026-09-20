package com.cutm.TeamPulse

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import com.cutm.TeamPulse.di.DatabaseEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TeamPulseApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    // REMOVED: @Inject lateinit var database: TeamPulseDatabase
    // That field forced eager injection before onCreate() could run

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    override fun onCreate() {
        val onCreateStart = System.currentTimeMillis()
        android.util.Log.d("Application", "onCreate() START at $onCreateStart")
        
        val superStart = System.currentTimeMillis()
        super.onCreate()
        val superEnd = System.currentTimeMillis()
        android.util.Log.d("Application", "super.onCreate() took ${superEnd - superStart}ms")
        
        // Warmup coroutine launch
        val warmupLaunchStart = System.currentTimeMillis()
        applicationScope.launch {
            android.util.Log.d("Application", "Warmup coroutine STARTED at ${System.currentTimeMillis()}")
            val startTime = System.currentTimeMillis()
            android.util.Log.d("Application", "Database warmup START at $startTime")
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@TeamPulseApplication,
                    DatabaseEntryPoint::class.java
                )
                val database = entryPoint.database()
                
                database.openHelper.writableDatabase
                val endTime = System.currentTimeMillis()
                android.util.Log.d("Application", "Database warmup DONE at $endTime (${endTime - startTime}ms)")
            } catch (e: Exception) {
                android.util.Log.e("Application", "Database warmup FAILED", e)
            }
        }
        val warmupLaunchEnd = System.currentTimeMillis()
        android.util.Log.d("Application", "Warmup coroutine LAUNCHED (took ${warmupLaunchEnd - warmupLaunchStart}ms)")
        
        val onCreateEnd = System.currentTimeMillis()
        android.util.Log.d("Application", "onCreate() COMPLETE at $onCreateEnd (TOTAL: ${onCreateEnd - onCreateStart}ms)")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }
}
