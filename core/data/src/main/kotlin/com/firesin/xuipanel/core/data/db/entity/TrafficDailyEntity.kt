package com.firesin.xuipanel.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "traffic_daily",
    primaryKeys = ["panel_id", "scope_kind", "scope_key", "day_epoch"],
    foreignKeys = [
        ForeignKey(
            entity = PanelEntity::class,
            parentColumns = ["id"],
            childColumns = ["panel_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "idx_traffic_daily_panel_day", value = ["panel_id", "day_epoch"]),
    ],
)
data class TrafficDailyEntity(
    @ColumnInfo(name = "panel_id")
    val panelId: String,

    @ColumnInfo(name = "scope_kind")
    val scopeKind: String,

    @ColumnInfo(name = "scope_key")
    val scopeKey: String,

    @ColumnInfo(name = "inbound_id")
    val inboundId: Int?,

    /** UTC midnight epoch millis */
    @ColumnInfo(name = "day_epoch")
    val dayEpoch: Long,

    @ColumnInfo(name = "up_delta")
    val upDelta: Long,

    @ColumnInfo(name = "down_delta")
    val downDelta: Long,
)
