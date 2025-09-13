package com.example.snsassistant

import android.app.Application
import com.example.snsassistant.data.db.AppDatabase
import com.example.snsassistant.data.openai.OpenAIClient
import com.example.snsassistant.data.repo.SnsRepository
import com.example.snsassistant.data.secure.SecurePrefs
import com.example.snsassistant.util.ServiceLocator

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val db = AppDatabase.build(this)
        val securePrefs = SecurePrefs(this)
        val openAIClient = OpenAIClient(securePrefs)
        val repository = SnsRepository(db.postDao(), db.replyDao(), openAIClient, securePrefs)

        ServiceLocator.init(repository)
    }
}

