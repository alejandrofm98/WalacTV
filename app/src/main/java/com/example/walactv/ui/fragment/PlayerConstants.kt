package com.example.walactv.ui.fragment

/**
 * Tuning constants for the player (timeouts, intervals, retry counts).
 * Kept as top-level `internal const` so they can be tweaked in one place
 * and accessed by PlayerFragment from the same module.
 */
internal const val MAX_RETRIES = 8
internal const val RETRY_DELAY_MS = 2_000L
internal const val FORCE_RESTART_DELAY_MS = 3_000L
internal const val BUFFERING_TIMEOUT_LIVE_MS = 20_000L
internal const val STALL_RECOVERY_MS = 8_000L
internal const val POSITION_CHECK_INTERVAL_MS = 5_000L
internal const val MAX_STUCK_CHECKS = 4
internal const val OVERLAY_DURATION_MS = 6_000L
internal const val DIRECT_ZAP_DELAY_MS = 1_500L
internal const val VOD_CONTROLLER_TIMEOUT_MS = 5_000
internal const val PROGRESS_SAVE_INTERVAL_MS = 30_000L
internal const val SEEK_RAPID_THRESHOLD_MS = 500L
