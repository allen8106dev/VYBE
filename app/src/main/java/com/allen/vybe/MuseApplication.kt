package com.allen.vybe

import android.app.Application
import androidx.room.Room
import com.allen.vybe.data.MuseDatabase
import com.allen.vybe.library.LocalPlaylistRepository
import com.allen.vybe.settings.MusePreferences
import com.allen.vybe.catalog.YouTubeRepository

class MuseApplication : Application() {
    override fun onCreate() { super.onCreate(); music }
    val database by lazy { Room.databaseBuilder(this, MuseDatabase::class.java, "muse.db").addMigrations(MuseDatabase.MIGRATION_1_2).build() }
    val localPlaylists by lazy { LocalPlaylistRepository(database) }
    val preferences by lazy { MusePreferences(this) }
    val youtube by lazy { YouTubeRepository() }
    val music by lazy { MusicController(this) }
}
