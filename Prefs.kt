package app.folio

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("folio_prefs")

data class AppSettings(
    val theme: Int = 0,
    val immersive: Boolean = false,
    val reduce: Boolean = false,
    val haptics: Boolean = true,
    val font: Int = 0,
    val grid: Boolean = true,
    val sort: Int = 0,
    val onboarded: Boolean = false,
    val finger: Boolean = true,
    val last: Long = 0L
)

object K {
    val theme = intPreferencesKey("theme")
    val immersive = booleanPreferencesKey("immersive")
    val reduce = booleanPreferencesKey("reduce")
    val haptics = booleanPreferencesKey("haptics")
    val font = intPreferencesKey("font")
    val grid = booleanPreferencesKey("grid")
    val sort = intPreferencesKey("sort")
    val onboarded = booleanPreferencesKey("onboarded")
    val finger = booleanPreferencesKey("finger")
    val last = longPreferencesKey("last")
}

class Prefs(private val c: Context) {
    val flow: Flow<AppSettings> = c.ds.data.map { p ->
        AppSettings(
            theme = p[K.theme] ?: 0,
            immersive = p[K.immersive] ?: false,
            reduce = p[K.reduce] ?: false,
            haptics = p[K.haptics] ?: true,
            font = p[K.font] ?: 0,
            grid = p[K.grid] ?: true,
            sort = p[K.sort] ?: 0,
            onboarded = p[K.onboarded] ?: false,
            finger = p[K.finger] ?: true,
            last = p[K.last] ?: 0L
        )
    }

    suspend fun <T> set(k: Preferences.Key<T>, v: T) { c.ds.edit { it[k] = v } }
}
