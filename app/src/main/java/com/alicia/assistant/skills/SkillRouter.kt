package com.alicia.assistant.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import com.alicia.assistant.service.LlmClient
import java.util.Date
import java.util.Locale

data class SkillResult(
    val success: Boolean,
    val response: String,
    val action: String? = null
)

class SkillRouter(private val context: Context, private val llmClient: LlmClient = LlmClient()) {

    suspend fun processInput(input: String, screenContext: String? = null): SkillResult {
        val lowerInput = input.lowercase().trim()

        return when {
            lowerInput.matches(Regex(".*(save|make|create|leave)\\s+(a\\s+)?(voice\\s+)?note.*")) ||
            lowerInput.matches(Regex(".*(remind|remember)\\s+me.*")) -> handleVoiceNote(input)

            lowerInput.matches(Regex("^(open|launch|start|go to|switch to)\\s+.*")) -> handleAppLauncher(input)

            lowerInput.contains("play") && lowerInput.contains("music") -> handleMusicControl(input)

            lowerInput.matches(Regex(".*(set|start|create)\\s+(a\\s+)?(timer|alarm).*")) ||
            lowerInput.matches(Regex("^(timer|alarm).*")) -> handleTimer(input)

            lowerInput.matches(Regex(".*(what('s|\\s+is)?\\s+the\\s+time|tell.*time|current\\s+time).*")) -> handleTimeQuery()

            lowerInput.matches(Regex(".*(what('s|\\s+is)?\\s+(the\\s+)?(date|day)|today('s|\\s+is)).*")) -> handleDateQuery()

            else -> {
                val prompt = if (screenContext != null) {
                    "[Screen content]\n$screenContext\n[End screen content]\n\nUser: $input"
                } else {
                    input
                }
                val response = llmClient.chat(prompt)
                SkillResult(
                    success = true,
                    response = response,
                    action = "llm_chat"
                )
            }
        }
    }
    
    private fun handleVoiceNote(input: String): SkillResult {
        return SkillResult(
            success = true,
            response = "To save a voice note, use the note recording button on the main screen.",
            action = "note_hint"
        )
    }
    
    private fun handleAppLauncher(input: String): SkillResult {
        val appName = input
            .replace(Regex("(open|launch|start|go to|switch to)\\s+(the\\s+)?", RegexOption.IGNORE_CASE), "")
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
        
        val match = appPackages.entries.firstOrNull { (key, _) ->
            appName.contains(key) || key.contains(appName)
        }
        val packageName = match?.value
        val displayName = match?.key ?: appName

        return if (packageName != null) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    SkillResult(
                        success = true,
                        response = "Opening $displayName",
                        action = "open_app"
                    )
                } else {
                    SkillResult(
                        success = false,
                        response = "$displayName is not installed on your device"
                    )
                }
            } catch (e: Exception) {
                Log.e("SkillRouter", "Failed to open app: $appName", e)
                SkillResult(
                    success = false,
                    response = "Failed to open $displayName"
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
            val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                SkillResult(
                    success = true,
                    response = "Opening Spotify to play music",
                    action = "play_music"
                )
            } else {
                val musicIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("music://")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(musicIntent)
                SkillResult(
                    success = true,
                    response = "Opening music app",
                    action = "play_music"
                )
            }
        } catch (e: Exception) {
            Log.e("SkillRouter", "Failed to open music app", e)
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
            Log.e("SkillRouter", "Failed to set timer", e)
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

}
