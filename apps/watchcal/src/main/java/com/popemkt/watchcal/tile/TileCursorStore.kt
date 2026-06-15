package com.popemkt.watchcal.tile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

private val Context.tileCursorPrefs: DataStore<Preferences> by
    androidx.datastore.preferences.preferencesDataStore(name = "tile_cursor")

/**
 * The one piece of tile state: which event the carousel is parked on. Persisted so a
 * glance returns the user where they left off; the renderer clamps it back into range
 * whenever the live list shrinks (specs/01-architecture.md § Tile surface).
 */
class TileCursorStore(private val context: Context) {

    suspend fun cursor(): Int =
        context.tileCursorPrefs.data.first()[KEY] ?: 0

    suspend fun setCursor(index: Int) {
        context.tileCursorPrefs.edit { it[KEY] = index }
    }

    private companion object {
        val KEY = intPreferencesKey("idx")
    }
}
