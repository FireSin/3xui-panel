package com.firesin.xuipanel.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.firesin.xuipanel.core.data.db.dao.AuditLogDao
import com.firesin.xuipanel.core.data.db.dao.PanelDao
import com.firesin.xuipanel.core.data.db.entity.AuditLogEntity
import com.firesin.xuipanel.core.data.db.entity.PanelEntity

@Database(
    entities = [PanelEntity::class, AuditLogEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun panelDao(): PanelDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE panels ADD COLUMN tls_mode TEXT NOT NULL DEFAULT 'SYSTEM'")
                db.execSQL("ALTER TABLE panels ADD COLUMN pinned_spki_sha256 TEXT")
                db.execSQL("ALTER TABLE panels ADD COLUMN pinned_at INTEGER")
                db.execSQL("UPDATE panels SET tls_mode = 'PINNED' WHERE trust_self_signed = 1")
            }
        }
    }
}
