package com.firesin.xuipanel.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.firesin.xuipanel.core.data.db.dao.AuditLogDao
import com.firesin.xuipanel.core.data.db.dao.PanelDao
import com.firesin.xuipanel.core.data.db.dao.TrafficDailyDao
import com.firesin.xuipanel.core.data.db.dao.TrafficStateDao
import com.firesin.xuipanel.core.data.db.entity.AuditLogEntity
import com.firesin.xuipanel.core.data.db.entity.PanelEntity
import com.firesin.xuipanel.core.data.db.entity.TrafficDailyEntity
import com.firesin.xuipanel.core.data.db.entity.TrafficStateEntity

@Database(
    entities = [PanelEntity::class, AuditLogEntity::class, TrafficStateEntity::class, TrafficDailyEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun panelDao(): PanelDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun trafficStateDao(): TrafficStateDao
    abstract fun trafficDailyDao(): TrafficDailyDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE panels ADD COLUMN tls_mode TEXT NOT NULL DEFAULT 'SYSTEM'")
                db.execSQL("ALTER TABLE panels ADD COLUMN pinned_spki_sha256 TEXT")
                db.execSQL("ALTER TABLE panels ADD COLUMN pinned_at INTEGER")
                db.execSQL("UPDATE panels SET tls_mode = 'PINNED' WHERE trust_self_signed = 1")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `traffic_state` (
                        `panel_id` TEXT NOT NULL,
                        `scope_kind` TEXT NOT NULL,
                        `scope_key` TEXT NOT NULL,
                        `inbound_id` INTEGER,
                        `last_up` INTEGER NOT NULL,
                        `last_down` INTEGER NOT NULL,
                        `last_sampled_at` INTEGER NOT NULL,
                        PRIMARY KEY(`panel_id`, `scope_kind`, `scope_key`),
                        FOREIGN KEY(`panel_id`) REFERENCES `panels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""",
                )
                db.execSQL(
                    """CREATE INDEX IF NOT EXISTS `idx_traffic_state_panel_kind_inbound`
                       ON `traffic_state` (`panel_id`, `scope_kind`, `inbound_id`)""",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `traffic_daily` (
                        `panel_id` TEXT NOT NULL,
                        `scope_kind` TEXT NOT NULL,
                        `scope_key` TEXT NOT NULL,
                        `inbound_id` INTEGER,
                        `day_epoch` INTEGER NOT NULL,
                        `up_delta` INTEGER NOT NULL,
                        `down_delta` INTEGER NOT NULL,
                        PRIMARY KEY(`panel_id`, `scope_kind`, `scope_key`, `day_epoch`),
                        FOREIGN KEY(`panel_id`) REFERENCES `panels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""",
                )
                db.execSQL(
                    """CREATE INDEX IF NOT EXISTS `idx_traffic_daily_panel_day`
                       ON `traffic_daily` (`panel_id`, `day_epoch`)""",
                )
            }
        }
    }
}
