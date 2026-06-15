package com.example.walactv.shared.domain

data class WatchProgressItem(
    val contentId: String,
    val contentType: String,
    val positionMs: Long,
    val durationMs: Long,
    val normalizedTitle: String,
    val title: String,
    val imageUrl: String,
    val seriesName: String?,
    val seriesProviderId: String? = null,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val lastWatchedAt: String,
    val isWatched: Boolean = false,
    val overview: String? = null,
    val overviewEn: String? = null,
    val voteAverage: Float? = null,
    val voteCount: Int? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val tagline: String? = null,
    val releaseDate: String? = null,
    val year: Int? = null,
    val tmdbTitle: String? = null,
    val totalSeasons: Int? = null,
) {
    val progressPercent: Int
        get() = if (durationMs > 0) ((positionMs * 100) / durationMs).toInt() else 0

    val isCompleted: Boolean
        get() = durationMs > 0 && positionMs >= durationMs * 95 / 100

    val shouldRestoreProgress: Boolean
        get() = !isWatched && positionMs > 60_000 && !isCompleted
}

fun buildTmdbImageUrl(path: String?, size: String): String? {
    val cleanPath = path?.takeUnless { it.equals("null", ignoreCase = true) }?.trim().orEmpty()
    if (cleanPath.isBlank()) return null
    if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) return cleanPath.replace("http://image.tmdb.org", "https://image.tmdb.org")
    val normalizedPath = if (cleanPath.startsWith("/")) cleanPath else "/$cleanPath"
    return "https://image.tmdb.org/t/p/$size$normalizedPath"
}
