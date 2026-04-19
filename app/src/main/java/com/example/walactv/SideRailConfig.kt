package com.example.walactv

internal enum class SideRailDestination {
    SEARCH,
    HOME,
    EVENTS,
    TV,
    MOVIES,
    SERIES,
    ANIME,
}

internal data class SideRailEntry(
    val label: String,
    val destination: SideRailDestination,
)

internal fun buildDefaultSideRailEntries(): List<SideRailEntry> {
    return listOf(
        SideRailEntry(label = "Buscar", destination = SideRailDestination.SEARCH),
        SideRailEntry(label = "Inicio", destination = SideRailDestination.HOME),
        SideRailEntry(label = "Eventos", destination = SideRailDestination.EVENTS),
        SideRailEntry(label = "TV en directo", destination = SideRailDestination.TV),
        SideRailEntry(label = "Peliculas", destination = SideRailDestination.MOVIES),
        SideRailEntry(label = "Series", destination = SideRailDestination.SERIES),
    )
}
