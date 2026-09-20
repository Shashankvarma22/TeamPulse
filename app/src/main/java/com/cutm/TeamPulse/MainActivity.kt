package com.cutm.TeamPulse

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cutm.TeamPulse.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val activityOnCreateStart = System.currentTimeMillis()
        android.util.Log.d("MainActivity", "onCreate() START at $activityOnCreateStart")
        
        val superStart = System.currentTimeMillis()
        super.onCreate(savedInstanceState)
        val superEnd = System.currentTimeMillis()
        android.util.Log.d("MainActivity", "super.onCreate() took ${superEnd - superStart}ms (includes Hilt injection)")
        
        val bindingStart = System.currentTimeMillis()
        binding = ActivityMainBinding.inflate(layoutInflater)
        val bindingEnd = System.currentTimeMillis()
        android.util.Log.d("MainActivity", "View binding inflation took ${bindingEnd - bindingStart}ms")
        
        val setContentStart = System.currentTimeMillis()
        setContentView(binding.root)
        val setContentEnd = System.currentTimeMillis()
        android.util.Log.d("MainActivity", "setContentView() took ${setContentEnd - setContentStart}ms")
        
        val activityOnCreateEnd = System.currentTimeMillis()
        android.util.Log.d("MainActivity", "onCreate() COMPLETE (TOTAL: ${activityOnCreateEnd - activityOnCreateStart}ms)")
    }
    
    override fun onStart() {
        val startTime = System.currentTimeMillis()
        android.util.Log.d("MainActivity", "onStart() called at $startTime")
        super.onStart()
        val endTime = System.currentTimeMillis()
        android.util.Log.d("MainActivity", "onStart() complete (${endTime - startTime}ms)")
    }
    
    override fun onResume() {
        val startTime = System.currentTimeMillis()
        android.util.Log.d("MainActivity", "onResume() called at $startTime")
        super.onResume()
        val endTime = System.currentTimeMillis()
        android.util.Log.d("MainActivity", "onResume() complete (${endTime - startTime}ms)")
    }
}
