package com.alicia.assistant.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class VoiceAssistantService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Wake word detection will be implemented here
        return START_STICKY
    }
}
