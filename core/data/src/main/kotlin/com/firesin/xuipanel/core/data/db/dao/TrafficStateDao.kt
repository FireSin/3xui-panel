package com.firesin.xuipanel.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.firesin.xuipanel.core.data.db.entity.TrafficStateEntity

@Dao
interface TrafficStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<TrafficStateEntity>)

    @Query("SELECT * FROM traffic_state WHERE panel_id = :panelId")
    suspend fun getAllForPanel(panelId: String): List<TrafficStateEntity>

    /** Returns the maximum last_sampled_at across all panels; null if no rows exist. */
    @Query("SELECT MAX(last_sampled_at) FROM traffic_state")
    suspend fun getMaxLastSampledAt(): Long?
}
