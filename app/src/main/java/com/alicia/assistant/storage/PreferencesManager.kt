package com.alicia.assistant.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.alicia.assistant.model.AppSettings
import com.alicia.assistant.model.VoiceCommand
import com.alicia.assistant.model.VoiceNote
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "alicia_prefs")

class PreferencesManager(private val context: Context) {
    
    private val gson = Gson()
    
    companion object {
        private val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        private val WAKE_WORD_SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")
        private val VOICE_FEEDBACK_ENABLED = booleanPreferencesKey("voice_feedback_enabled")
        private val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        private val RECENT_COMMANDS = stringPreferencesKey("recent_commands")
        private val VOICE_NOTES = stringPreferencesKey("voice_notes")
    }
    
    // Settings
    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[WAKE_WORD_ENABLED] = settings.wakeWordEnabled
            prefs[WAKE_WORD_SENSITIVITY] = settings.wakeWordSensitivity
            prefs[VOICE_FEEDBACK_ENABLED] = settings.voiceFeedbackEnabled
            prefs[HAPTIC_FEEDBACK_ENABLED] = settings.hapticFeedbackEnabled
        }
    }
    
    suspend fun getSettings(): AppSettings {
        val prefs = context.dataStore.data.first()
        return AppSettings(
            wakeWordEnabled = prefs[WAKE_WORD_ENABLED] ?: false,
            wakeWordSensitivity = prefs[WAKE_WORD_SENSITIVITY] ?: 0.7f,
            voiceFeedbackEnabled = prefs[VOICE_FEEDBACK_ENABLED] ?: true,
            hapticFeedbackEnabled = prefs[HAPTIC_FEEDBACK_ENABLED] ?: true
        )
    }
    
    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            wakeWordEnabled = prefs[WAKE_WORD_ENABLED] ?: false,
            wakeWordSensitivity = prefs[WAKE_WORD_SENSITIVITY] ?: 0.7f,
            voiceFeedbackEnabled = prefs[VOICE_FEEDBACK_ENABLED] ?: true,
            hapticFeedbackEnabled = prefs[HAPTIC_FEEDBACK_ENABLED] ?: true
        )
    }
    
    // Recent Commands
    suspend fun saveCommand(command: VoiceCommand) {
        val commands = getRecentCommands().toMutableList()
        commands.add(0, command)
        if (commands.size > 10) {
            commands.removeAt(commands.size - 1)
        }
        
        val json = gson.toJson(commands)
        context.dataStore.edit { prefs ->
            prefs[RECENT_COMMANDS] = json
        }
    }
    
    suspend fun getRecentCommands(): List<VoiceCommand> {
        val prefs = context.dataStore.data.first()
        val json = prefs[RECENT_COMMANDS] ?: return emptyList()
        val type = object : TypeToken<List<VoiceCommand>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    suspend fun clearCommands() {
        context.dataStore.edit { prefs ->
            prefs.remove(RECENT_COMMANDS)
        }
    }
    
    // Voice Notes
    suspend fun saveNote(note: VoiceNote) {
        val notes = getNotes().toMutableList()
        notes.add(0, note)
        
        val json = gson.toJson(notes)
        context.dataStore.edit { prefs ->
            prefs[VOICE_NOTES] = json
        }
    }
    
    suspend fun getNotes(): List<VoiceNote> {
        val prefs = context.dataStore.data.first()
        val json = prefs[VOICE_NOTES] ?: return emptyList()
        val type = object : TypeToken<List<VoiceNote>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    suspend fun deleteNote(noteId: String) {
        val notes = getNotes().toMutableList()
        notes.removeAll { it.id == noteId }
        
        val json = gson.toJson(notes)
        context.dataStore.edit { prefs ->
            prefs[VOICE_NOTES] = json
        }
    }
    
    suspend fun clearNotes() {
        context.dataStore.edit { prefs ->
            prefs.remove(VOICE_NOTES)
        }
    }
}
