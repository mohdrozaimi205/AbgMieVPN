package com.abgmie.vpn.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY countryCode, city")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers")
    suspend fun all(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun get(id: Long): ServerEntity?

    @Insert
    suspend fun insert(s: ServerEntity): Long

    @Update
    suspend fun update(s: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE servers SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE servers SET lastPingMs = :ping, lastPingAt = :at WHERE id = :id")
    suspend fun setPing(id: Long, ping: Int?, at: Long)

    @Query("UPDATE servers SET lastConnectedAt = :at, lastOk = :ok WHERE id = :id")
    suspend fun markConnected(id: Long, at: Long, ok: Boolean)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Insert
    suspend fun insert(s: SessionEntity): Long

    @Query("UPDATE sessions SET rxBytes = :rx, txBytes = :tx, endedAt = :end WHERE id = :id")
    suspend fun updateTotals(id: Long, rx: Long, tx: Long, end: Long?)

    @Query("UPDATE sessions SET endedAt = :end WHERE endedAt IS NULL")
    suspend fun closeDangling(end: Long)

    @Query("DELETE FROM sessions")
    suspend fun clear()
}

@Database(entities = [ServerEntity::class, SessionEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun servers(): ServerDao
    abstract fun sessions(): SessionDao
}
