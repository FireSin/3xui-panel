package com.firesin.xuipanel.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "traffic_state",
    primaryKeys = ["panel_id", "scope_kind", "scope_key"],
    foreignKeys = [
        ForeignKey(
            entity = PanelEntity::class,
            parentColumns = ["id"],
            childColumns = ["panel_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(name = "idx_traffic_state_panel_kind_inbound", value = ["panel_id", "scope_kind", "inbound_id"]),
    ],
)
data class TrafficStateEntity(
    @ColumnInfo(name = "panel_id")
    val panelId: String,

    /** "INBOUND" or "CLIENT" */
    @ColumnInfo(name = "scope_kind")
    val scopeKind: String,

    /** inbound id as String (for INBOUND) or email (for CLIENT) */
    @ColumnInfo(name = "scope_key")
    val scopeKey: String,

    /** Parent inbound id for CLIENT scope; null for INBOUND scope */
    @ColumnInfo(name = "inbound_id")
    val inboundId: Int?,

    @ColumnInfo(name = "last_up")
    val lastUp: Long,

    @ColumnInfo(name = "last_down")
    val lastDown: Long,

    /** Epoch millis of the last successful sample */
    @ColumnInfo(name = "last_sampled_at")
    val lastSampledAt: Long,
)
