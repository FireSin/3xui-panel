package com.firesin.xuipanel.core.data.repository

/**
 * User-facing schedule options for automatic DB backup.
 *
 * OFF      — feature disabled, no periodic work scheduled.
 * DAILY    — 24-hour periodic work.
 * WEEKLY   — 7-day periodic work.
 */
enum class AutoBackupSchedule {
    OFF,
    DAILY,
    WEEKLY,
}
