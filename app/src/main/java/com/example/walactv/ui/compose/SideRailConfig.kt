package com.example.walactv.ui.compose

internal enum class SideRailDestination {
    SEARCH,
    HOME,
    EVENTS,
    TV,
    DISCOVER,
}

internal data class SideRailEntry(
    val label: String,
    val destination: SideRailDestination,
)

internal fun buildDefaultSideRailEntries(includeIptv: Boolean = true): List<SideRailEntry> {
    return buildList {
        add(SideRailEntry(label = "Buscar", destination = SideRailDestination.SEARCH))
        add(SideRailEntry(label = "Inicio", destination = SideRailDestination.HOME))
        if (includeIptv) {
            add(SideRailEntry(label = "Eventos", destination = SideRailDestination.EVENTS))
            add(SideRailEntry(label = "TV en directo", destination = SideRailDestination.TV))
        }
        add(SideRailEntry(label = "Discover", destination = SideRailDestination.DISCOVER))
    }
}
