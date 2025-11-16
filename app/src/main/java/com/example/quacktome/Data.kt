package com.example.quacktome

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// --- Data Classes ---
data class Conversation(
    val id: Int,
    val name: String,
    val messages: List<Message>
)

data class Message(val text: String, val isFromUser: Boolean)

// --- DataRepository ---
class DataRepository(context: Context) {

    private val gson = Gson()

    private val themePrefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    fun saveThemePreference(useDarkTheme: Boolean) {
        themePrefs.edit().putBoolean("dark_theme", useDarkTheme).apply()
    }

    fun loadThemePreference(): Boolean {
        return themePrefs.getBoolean("dark_theme", false)
    }

    private val conversationPrefs: SharedPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)

    fun saveConversations(conversations: List<Conversation>) {
        val json = gson.toJson(conversations)
        conversationPrefs.edit().putString("conversation_history", json).apply()
    }

    fun loadConversations(): List<Conversation> {
        val json = conversationPrefs.getString("conversation_history", null)
        return if (json != null) {
            val type = object : TypeToken<List<Conversation>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }
}