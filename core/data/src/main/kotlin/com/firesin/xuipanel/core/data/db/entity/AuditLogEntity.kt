package com.firesin.xuipanel.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_log",
    foreignKeys = [
        ForeignKey(
            entity = PanelEntity::class,
            parentColumns = ["id"],
            childColumns = ["panel_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("panel_id")],
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "panel_id")
    val panelId: String?,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "outcome")
    val outcome: String,

    @ColumnInfo(name = "message")
    val message: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
