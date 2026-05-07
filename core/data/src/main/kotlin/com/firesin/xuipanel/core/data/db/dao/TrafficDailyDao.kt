package com.firesin.xuipanel.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.firesin.xuipanel.core.data.db.entity.TrafficDailyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficDailyDao {

    /**
     * Upserts rows. On conflict (same PK), increments the deltas rather than replacing them,
     * because multiple ticks may land in the same day bucket.
     *
     * Room does not support "INSERT OR UPDATE ... SET x = x + ?" natively, so we use two
     * separate queries: insert-or-ignore then a bulk update.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(rows: List<TrafficDailyEntity>)

    @Query(
        """UPDATE traffic_daily
           SET up_delta = up_delta + :upDelta, down_delta = down_delta + :downDelta
           WHERE panel_id = :panelId AND scope_kind = :scopeKind
             AND scope_key = :scopeKey AND day_epoch = :dayEpoch""",
    )
    suspend fun increment(
        panelId: String,
        scopeKind: String,
        scopeKey: String,
        dayEpoch: Long,
        upDelta: Long,
        downDelta: Long,
    )

    @Query(
        """SELECT * FROM traffic_daily
           WHERE panel_id = :panelId AND scope_kind = 'INBOUND' AND scope_key = :inboundKey
             AND day_epoch >= :fromDay AND day_epoch <= :toDay
           ORDER BY day_epoch ASC""",
    )
    fun observeForInbound(
        panelId: String,
        inboundKey: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<TrafficDailyEntity>>

    @Query(
        """SELECT * FROM traffic_daily
           WHERE panel_id = :panelId AND scope_kind = 'CLIENT' AND scope_key = :emailKey
             AND inbound_id = :inboundId AND day_epoch >= :fromDay AND day_epoch <= :toDay
           ORDER BY day_epoch ASC""",
    )
    fun observeForClient(
        panelId: String,
        inboundId: Int,
        emailKey: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<TrafficDailyEntity>>

    @Query("DELETE FROM traffic_daily WHERE day_epoch < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
