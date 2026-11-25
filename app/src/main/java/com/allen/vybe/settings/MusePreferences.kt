package com.allen.vybe.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.musePreferences by preferencesDataStore("muse")
class MusePreferences(context: Context) {
    private val store = context.applicationContext.musePreferences
    private val motion = booleanPreferencesKey("reduced_motion")
    private val searches = stringPreferencesKey("recent_searches")
    private val autoplayKey = booleanPreferencesKey("autoplay_suggestions")
    val autoplay = store.data.map { it[autoplayKey] ?: true }
    suspend fun autoplay(value: Boolean) { store.edit { it[autoplayKey] = value } }
    val reducedMotion = store.data.map { it[motion] ?: false }
    val recentSearches = store.data.map { it[searches].orEmpty().split('\n').filter(String::isNotBlank) }
    suspend fun reduceMotion(value: Boolean) { store.edit { it[motion] = value } }
    suspend fun recordSearch(query: String) { store.edit { prefs ->
        val recent = prefs[searches].orEmpty().split('\n').filter { it.isNotBlank() && it != query }
        prefs[searches] = (listOf(query.replace('\n', ' ')) + recent).take(8).joinToString("\n")
    } }
    suspend fun clearSearches() { store.edit { it.remove(searches) } }
}
