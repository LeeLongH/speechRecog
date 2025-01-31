package com.example.speechrecognitionapp

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.example.speechrecognitionapp.databinding.FragmentHomeBinding

class HomeFragment : Fragment(), RecordingCallback {

    private var audioRecordingService: AudioRecordingService? = null
    private var isServiceBound: Boolean = false
    private lateinit var binding: FragmentHomeBinding

    private var results = ArrayList<Result>()
    private lateinit var adapter: ResultAdapter

    private var sharedPreferences: SharedPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = activity?.let { PreferenceManager.getDefaultSharedPreferences(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater)
        val view = binding.root

        val listView = binding.listView
        adapter = ResultAdapter(results, activity?.applicationContext)
        listView.adapter = adapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Button: dB
        binding.btnRecord.setOnClickListener {
            if (isServiceBound) {
                // Stop the service if running
                binding.btnRecord.text = "Record with dB"
                stopService()
                (activity as? MainActivity)?.writeFirebase()
            } else {
                // Start the service for dB
                binding.btnRecord.text = "Stop"
                startServiceWithMethod("dB")
            }
        }

        // Button: Silero
        binding.btnRecordSilero.setOnClickListener {
            if (isServiceBound) {
                // Stop the service if running
                binding.btnRecordSilero.text = "Record with Silero"
                stopService()
                (activity as? MainActivity)?.writeFirebase()
            } else {
                // Start the service for Silero
                binding.btnRecordSilero.text = "Stop"
                startServiceWithMethod("Silero")
            }
        }
    }

    /**
     * Pass along user‐configured thresholds + selected method to the service
     */
    private fun startServiceWithMethod(method: String) {
        val intent = Intent(requireActivity(), AudioRecordingService::class.java)
        intent.putExtra("method", method)

        try {
            val energyThreshold = sharedPreferences?.getString("energy", "0.1")
            val probabilityThreshold = sharedPreferences?.getString("probability", "0.002")
            val windowSize = sharedPreferences?.getString("window_size", "8000")
            val topK = sharedPreferences?.getString("top_k", "1")

            intent.putExtras(Bundle().apply {
                putDouble("energyThreshold", energyThreshold?.toDouble()!!)
                putFloat("probabilityThreshold", probabilityThreshold?.toFloat()!!)
                putInt("windowSize", windowSize?.toInt()!!)
                putInt("topK", topK?.toInt()!!)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start & bind
        requireActivity().startService(intent)
        bindService()
    }

    override fun onDataUpdated(data: ArrayList<Result>) {
        Log.d(TAG, "Updated: ${data.size}")
        activity?.runOnUiThread {
            adapter.clear()
            adapter.addAll(data)
            adapter.notifyDataSetChanged()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as AudioRecordingService.RunServiceBinder
            audioRecordingService = binder.service
            audioRecordingService?.setCallback(this@HomeFragment)
            isServiceBound = true
            audioRecordingService?.background()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioRecordingService = null
            isServiceBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            activity?.unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound && audioRecordingService != null) {
            if (audioRecordingService?.isRecording == true) {
                Log.d(TAG, "Foregrounding service")
                audioRecordingService?.foreground()
            }
        } else {
            Log.d(TAG, "Stopping service from onStop")
            stopService()
        }
    }

    private fun stopService() {
        if (isServiceBound) {
            unbindService()
            isServiceBound = false
        }
        val serviceIntent = Intent(activity, AudioRecordingService::class.java)
        activity?.stopService(serviceIntent)
    }

    private fun bindService() {
        Log.d(TAG, "Binding to AudioRecordingService")
        val bindIntent = Intent(activity, AudioRecordingService::class.java)
        val success = activity?.bindService(bindIntent, serviceConnection, AppCompatActivity.BIND_AUTO_CREATE)
        isServiceBound = success == true
    }

    private fun unbindService() {
        if (isServiceBound) {
            activity?.unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    companion object {
        private val TAG = HomeFragment::class.java.simpleName
    }
}
