package com.firesin.xuipanel.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.firesin.xuipanel.core.data.db.entity.PanelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PanelDao {

    @Query("SELECT * FROM panels ORDER BY created_at ASC")
    fun observeAll(): Flow<List<PanelEntity>>

    @Query("SELECT * FROM panels ORDER BY created_at ASC")
    suspend fun getAll(): List<PanelEntity>

    @Query("SELECT * FROM panels WHERE id = :id")
    suspend fun getById(id: String): PanelEntity?

    @Query("SELECT * FROM panels WHERE is_active = 1 LIMIT 1")
    suspend fun getActive(): PanelEntity?

    @Query("SELECT * FROM panels WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<PanelEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(panel: PanelEntity)

    @Query("DELETE FROM panels WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE panels SET is_active = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE panels SET is_active = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Query("UPDATE panels SET pinned_spki_sha256 = :spkiBase64, pinned_at = :pinnedAt, tls_mode = 'PINNED' WHERE id = :id")
    suspend fun updatePin(id: String, spkiBase64: String, pinnedAt: Long)

    @Query("DELETE FROM panels")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(panels: List<PanelEntity>)

    /**
     * Atomically clears the active flag from all panels, then sets it on [id].
     * Ensures at most one active panel at all times.
     */
    @Transaction
    suspend fun setActivePanel(id: String) {
        clearActiveFlag()
        setActive(id)
    }
}
