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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


import com.example.speechrecognitionapp.databinding.ActivityMainBinding
import com.example.speechrecognitionapp.logging.LoggingManager
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray

class MainActivity : AppCompatActivity()/*, RecordingCallback*/ {

    private lateinit var binding: ActivityMainBinding

    var firebaseDatabase: FirebaseDatabase? = null
    var databaseReference: DatabaseReference? = null

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
        FirebaseApp.initializeApp(this)

        firebaseDatabase = FirebaseDatabase.getInstance("https://speechrecognitionapp-d1fcb-default-rtdb.firebaseio.com/")
        databaseReference = firebaseDatabase!!.getReference("words") //

    }

    fun writeFirebase(databaseReference: DatabaseReference? = this.databaseReference) {
        try {
            // 1) Fetch logs
            val logs = LoggingManager.fetchAndClearLogs(applicationContext)
            if (logs.isNotBlank()) {
                // 2) Push logs to Realtime Database
                val dbTask = databaseReference?.push()?.setValue(logs)
                // Wait for completion (blocks this thread)
                //Tasks.await(dbTask)
                Log.d(TAG, "Successfully pushed logs to Realtime Database.")
            } else {
                Log.d(TAG, "No logs to push at this time.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading logs", e)
        }
    }

    private fun transformLogs(logs: String): String {
        // Example logs: [{"Word":"cat"},{"Word":"six"},{"Word":"sheila"}...]
        val originalArray = JSONArray(logs)
        val newArray = JSONArray()

        for (i in 0 until originalArray.length()) {
            val obj = originalArray.getJSONObject(i)
            val word = obj.optString("Word") // e.g. "cat"
            newArray.put(word)              // add "cat" to the new array
        }

        // newArray is now something like ["cat","six","sheila","two","three","two"]
        return newArray.toString()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.fragmentContainerView)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu_bar, menu)
        return super.onCreateOptionsMenu(menu)
    }


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
        private const val PREF_NAME = "MyPreferences"
        private const val KEY_ONE = "ONE"
    }
}