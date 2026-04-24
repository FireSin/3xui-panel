package com.firesin.xuipanel.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.firesin.xuipanel.core.data.db.dao.AuditLogDao
import com.firesin.xuipanel.core.data.db.dao.PanelDao
import com.firesin.xuipanel.core.data.db.entity.AuditLogEntity
import com.firesin.xuipanel.core.data.db.entity.PanelEntity

@Database(
    entities = [PanelEntity::class, AuditLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun panelDao(): PanelDao
    abstract fun auditLogDao(): AuditLogDao
}
