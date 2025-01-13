package com.example.speechrecognitionapp

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.constraintlayout.widget.Constraints
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
/* PAUL
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
*/

import com.example.speechrecognitionapp.databinding.ActivityMainBinding
import com.example.speechrecognitionapp.workers.LogUploadWorker
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity()/*, RecordingCallback*/ {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view: View = binding.root
        setContentView(view)

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        try {
            setupActionBarWithNavController(findNavController(R.id.fragmentContainerView))
        } catch (e: Exception) {
            Log.d(TAG, "Error: " + e.message)
        }

        //scheduleLogUpload() PUAL
    }

    /*private fun scheduleLogUpload() { PAUL
        // WorkManager constraints:
        val constraints = Constraints.Builder()git stash list
                .setRequiredNetworkType(NetworkType.UNMETERED) //Wi-Fi Only
            .build()

        // PeriodicWorkRequest: run every 15 minutes (minimum on Android).
        val uploadWorkRequest = PeriodicWorkRequestBuilder<LogUploadWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        // Enqueue the work
        WorkManager.getInstance(this).enqueue(uploadWorkRequest)
    }

    private fun PeriodicWorkRequestBuilder(i: Int, unit: TimeUnit) {}

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.fragmentContainerView)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu_bar, menu)
        return super.onCreateOptionsMenu(menu)
    }*/

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId){
            R.id.nav_home -> {
                findNavController(R.id.fragmentContainerView).navigate(R.id.action_settingsFragment_to_homeFragment)
            }
            R.id.nav_settings -> {
                findNavController(R.id.fragmentContainerView).navigate(R.id.action_homeFragment_to_settingsFragment)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == 1){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                // Permission granted
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                // Permission denied
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.simpleName
    }
}