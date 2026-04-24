package com.firesin.xuipanel.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.firesin.xuipanel.core.data.db.entity.AuditLogEntity

@Dao
interface AuditLogDao {

    @Insert
    suspend fun insert(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_log ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<AuditLogEntity>
}
