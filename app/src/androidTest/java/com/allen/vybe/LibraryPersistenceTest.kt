package com.allen.vybe

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.allen.vybe.domain.Track
import com.allen.vybe.data.MuseDatabase
import com.allen.vybe.library.LocalPlaylistRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryPersistenceTest {
    @Test fun playlistSurvivesReopenAndReordersWithoutDuplicates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, MuseDatabase::class.java, name).build()
        try {
            var repository = LocalPlaylistRepository(db)
            val id = repository.create("  Test mix  ")
            val tracks = listOf(Track("7wtfhZwyrcc","Believer","Imagine Dragons"),Track("aqz-KE-bpKQ","Big Buck Bunny","Blender"),Track("M7lc1UVf-VE","Player demo","YouTube"))
            tracks.forEach { repository.add(id, it) }
            repository.add(id, tracks[0])
            repository.move(id, tracks[2], 0)
            repository.rename(id, "Renamed", "Local mix")
            val copy=repository.copy("Independent copy","Copied description",repository.contents(id)+tracks[0])
            assertEquals(3,repository.contents(copy).size)
            assertEquals("Copied description",repository.playlists().first{it.id==copy}.description)
            db.library().like(com.allen.vybe.data.FavouriteEntity(tracks[0].id))
            db.library().record(tracks[1].id,System.currentTimeMillis())
            db.close()
            db = Room.databaseBuilder(context, MuseDatabase::class.java, name).build()
            repository = LocalPlaylistRepository(db)
            assertEquals("Renamed", repository.playlists().first{it.id==id}.title)
            assertEquals(listOf(tracks[2].id, tracks[0].id, tracks[1].id), repository.contents(id).map { it.id })
            assertEquals(tracks[0].title,db.library().likedSongs().first().single().title)
            assertEquals(tracks[1].id,db.library().recentSongs().first().single().id)
            db.library().clearHistory()
            assertTrue(db.library().recentSongs().first().isEmpty())
            assertEquals(1,db.library().likedSongs().first().size)
            repository.remove(id, tracks[0])
            assertEquals(2, repository.contents(id).size)
            repository.delete(id)
            assertEquals(3,repository.contents(copy).size)
            assertEquals(1,db.library().likedSongs().first().size)
            repository.delete(copy)
            assertTrue(repository.playlists().isEmpty())
            assertTrue(db.library().entries(id).isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun originalDatabaseMigratesWithoutLosingPlaylistNames() = runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val ctx=ins.targetContext;val name="migration-youtube-test.db"
        ctx.deleteDatabase(name)
        val path=ctx.getDatabasePath(name);path.parentFile!!.mkdirs()
        val schema=org.json.JSONObject(ins.context.assets.open("com.allen.vybe.data.MuseDatabase/1.json").bufferedReader().use{it.readText()}).getJSONObject("database")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path,null).use {old ->
            val entities=schema.getJSONArray("entities")
            for(n in 0 until entities.length()){
                val entity=entities.getJSONObject(n);old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
                val indices=entity.optJSONArray("indices")?:org.json.JSONArray()
                for(i in 0 until indices.length())old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
            }
            val setup=schema.getJSONArray("setupQueries");for(n in 0 until setup.length())old.execSQL(setup.getString(n))
            old.execSQL("INSERT INTO playlists(id,title,description) VALUES('kept','Keep my playlist','')")
            old.execSQL("INSERT INTO entries(playlistId,trackId,position) VALUES('kept','muse-dawn',0)")
            old.version=1
        }
        val upgraded=Room.databaseBuilder(ctx,MuseDatabase::class.java,name).addMigrations(MuseDatabase.MIGRATION_1_2).build()
        try{assertEquals("Keep my playlist",upgraded.library().playlists().single().title);assertTrue(upgraded.library().entries("kept").isEmpty());assertTrue(upgraded.library().likedSongs().first().isEmpty())}
        finally{upgraded.close();ctx.deleteDatabase(name)}
    }
}
