package com.gtky.app

import android.app.Application
import com.gtky.app.data.database.DataSeeder
import com.gtky.app.data.database.GTKYDatabase
import com.gtky.app.data.repository.GTKYRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GTKYApplication : Application() {

    private val database by lazy { GTKYDatabase.getInstance(this) }
    val repository by lazy { GTKYRepository(database) }

    private val appScope = CoroutineScope(Dispatchers.IO)

    private val _language = MutableStateFlow("en")
    val language: StateFlow<String> = _language.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            DataSeeder.seedIfNeeded(database)
            _language.value = repository.getLanguage()
        }
    }

    fun toggleLanguage() {
        appScope.launch {
            val new = if (_language.value == "en") "es" else "en"
            repository.setLanguage(new)
            _language.value = new
        }
    }
}
