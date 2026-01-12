package com.alicia.assistant.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.speech.tts.TextToSpeech
import java.util.*

data class SkillResult(
    val success: Boolean,
    val response: String,
    val action: String? = null
)

class SkillRouter(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    
    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }
    
    suspend fun processCommand(input: String): SkillResult {
        val lowerInput = input.lowercase().trim()
        
        // Try each skill in priority order
        return when {
            // Voice notes skill (highest priority)
            lowerInput.contains("note") || lowerInput.contains("remind") -> {
                handleVoiceNote(input)
            }
            
            // App launcher skill
            lowerInput.contains("open") || lowerInput.contains("launch") -> {
                handleAppLauncher(input)
            }
            
            // Music skill
            lowerInput.contains("play") && lowerInput.contains("music") -> {
                handleMusicControl(input)
            }
            
            // Timer skill
            lowerInput.contains("timer") || lowerInput.contains("alarm") -> {
                handleTimer(input)
            }
            
            // Time query
            lowerInput.contains("time") -> {
                handleTimeQuery()
            }
            
            // Date query
            lowerInput.contains("date") || lowerInput.contains("day") -> {
                handleDateQuery()
            }
            
            // Weather query
            lowerInput.contains("weather") -> {
                handleWeatherQuery()
            }
            
            // Default assistant response
            else -> {
                SkillResult(
                    success = false,
                    response = "I can help you open apps, play music, save notes, or answer basic questions. What would you like to do?"
                )
            }
        }
    }
    
    private fun handleVoiceNote(input: String): SkillResult {
        // Extract note content
        val noteContent = input
            .replace(Regex("(leave|save|create|make)\\s+(a\\s+)?(voice\\s+)?note", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(remind|remember)\\s+(me\\s+)?(to\\s+)?", RegexOption.IGNORE_CASE), "")
            .trim()
        
        if (noteContent.isEmpty()) {
            return SkillResult(
                success = false,
                response = "What would you like me to note?"
            )
        }
        
        // Save note (implementation in PreferencesManager)
        return SkillResult(
            success = true,
            response = "Voice note saved: $noteContent",
            action = "save_note"
        )
    }
    
    private fun handleAppLauncher(input: String): SkillResult {
        val appName = input
            .replace(Regex("(open|launch|start|go to|switch to)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .lowercase()
        
        val appPackages = mapOf(
            "spotify" to "com.spotify.music",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "twitter" to "com.twitter.android",
            "facebook" to "com.facebook.katana",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger"
        )
        
        val packageName = appPackages[appName]
        
        return if (packageName != null) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    context.startActivity(intent)
                    SkillResult(
                        success = true,
                        response = "Opening $appName",
                        action = "open_app"
                    )
                } else {
                    SkillResult(
                        success = false,
                        response = "$appName is not installed on your device"
                    )
                }
            } catch (e: Exception) {
                SkillResult(
                    success = false,
                    response = "Failed to open $appName"
                )
            }
        } else {
            SkillResult(
                success = false,
                response = "I don't know how to open $appName"
            )
        }
    }
    
    private fun handleMusicControl(input: String): SkillResult {
        return try {
            // Try to open Spotify
            val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (intent != null) {
                context.startActivity(intent)
                SkillResult(
                    success = true,
                    response = "Opening Spotify to play music",
                    action = "play_music"
                )
            } else {
                // Fallback to generic music intent
                val musicIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("music://")
                }
                context.startActivity(musicIntent)
                SkillResult(
                    success = true,
                    response = "Opening music app",
                    action = "play_music"
                )
            }
        } catch (e: Exception) {
            SkillResult(
                success = false,
                response = "Please open your music app manually"
            )
        }
    }
    
    private fun handleTimer(input: String): SkillResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            SkillResult(
                success = true,
                response = "Opening clock app to set a timer",
                action = "set_timer"
            )
        } catch (e: Exception) {
            SkillResult(
                success = false,
                response = "Please open your clock app to set a timer"
            )
        }
    }
    
    private fun handleTimeQuery(): SkillResult {
        val currentTime = java.text.SimpleDateFormat("h:mm a", Locale.US)
            .format(Date())
        return SkillResult(
            success = true,
            response = "The current time is $currentTime",
            action = "time_query"
        )
    }
    
    private fun handleDateQuery(): SkillResult {
        val currentDate = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
            .format(Date())
        return SkillResult(
            success = true,
            response = "Today is $currentDate",
            action = "date_query"
        )
    }
    
    private fun handleWeatherQuery(): SkillResult {
        return SkillResult(
            success = false,
            response = "I don't have access to weather data yet. Please check your weather app!"
        )
    }
    
    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }
}
