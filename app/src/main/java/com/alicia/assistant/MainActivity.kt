package com.alicia.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.alicia.assistant.databinding.ActivityMainBinding
import com.alicia.assistant.model.VoiceCommand
import com.alicia.assistant.service.VoiceRecognitionManager
import com.alicia.assistant.skills.SkillRouter
import com.alicia.assistant.storage.PreferencesManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var voiceRecognitionManager: VoiceRecognitionManager
    private lateinit var skillRouter: SkillRouter
    private lateinit var preferencesManager: PreferencesManager
    private var isListening = false
    
    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize managers
        preferencesManager = PreferencesManager(this)
        skillRouter = SkillRouter(this)
        voiceRecognitionManager = VoiceRecognitionManager(this)
        
        setupUI()
        checkPermissions()
        loadRecentCommands()
    }
    
    private fun setupUI() {
        // Activation button
        binding.activationButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }
        
        // Navigation buttons
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        binding.notesButton.setOnClickListener {
            startActivity(Intent(this, VoiceNotesActivity::class.java))
        }
        
        // Setup RecyclerView for recent commands
        binding.recentCommandsRecyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Permission Required")
                        .setMessage("Microphone permission is required for voice commands.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
    
    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            checkPermissions()
            return
        }
        
        isListening = true
        updateUIForListening(true)
        
        voiceRecognitionManager.startListening { recognizedText ->
            lifecycleScope.launch {
                processVoiceCommand(recognizedText)
            }
        }
    }
    
    private fun stopListening() {
        isListening = false
        updateUIForListening(false)
        voiceRecognitionManager.stopListening()
    }
    
    private fun updateUIForListening(listening: Boolean) {
        binding.statusText.text = if (listening) {
            getString(R.string.listening)
        } else {
            getString(R.string.tap_to_speak)
        }
        
        if (listening) {
            // Start pulsing animation
            val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse)
            binding.activationButton.startAnimation(pulseAnimation)
            binding.waveRing1.visibility = View.VISIBLE
            binding.waveRing2.visibility = View.VISIBLE
        } else {
            binding.activationButton.clearAnimation()
            binding.waveRing1.visibility = View.GONE
            binding.waveRing2.visibility = View.GONE
        }
    }
    
    private suspend fun processVoiceCommand(text: String) {
        runOnUiThread {
            binding.transcribedText.text = text
            binding.transcribedText.visibility = View.VISIBLE
            binding.responseText.visibility = View.GONE
        }
        
        // Process through skill router
        val result = skillRouter.processCommand(text)
        
        runOnUiThread {
            binding.responseText.text = result.response
            binding.responseText.visibility = View.VISIBLE
            stopListening()
            
            // Save command
            val command = VoiceCommand(
                text = text,
                response = result.response,
                timestamp = System.currentTimeMillis()
            )
            saveCommand(command)
            loadRecentCommands()
        }
    }
    
    private fun saveCommand(command: VoiceCommand) {
        lifecycleScope.launch {
            preferencesManager.saveCommand(command)
        }
    }
    
    private fun loadRecentCommands() {
        lifecycleScope.launch {
            val commands = preferencesManager.getRecentCommands()
            runOnUiThread {
                if (commands.isEmpty()) {
                    binding.recentCommandsSection.visibility = View.GONE
                } else {
                    binding.recentCommandsSection.visibility = View.VISIBLE
                    // Update RecyclerView adapter here
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceRecognitionManager.destroy()
    }
}
