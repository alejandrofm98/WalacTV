package com.example.walactv.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walactv.shared.domain.FormFactor
import com.example.walactv.shared.domain.FormFactorDetector
import com.example.walactv.shared.ui.theme.*

@Composable
fun AdaptiveNavigationRail(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompact = FormFactorDetector.current == FormFactor.MOBILE

    if (isCompact) {
        BottomNavigationBar(items, selectedIndex, onItemSelected, modifier)
    } else {
        SideNavigationRail(items, selectedIndex, onItemSelected, modifier)
    }
}

@Composable
private fun BottomNavigationBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        containerColor = IptvSurface,
        modifier = modifier,
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selectedIndex,
                onClick = { onItemSelected(index) },
                icon = { Text(item.icon, fontSize = 20.sp) },
                label = { Text(item.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = IptvAccent,
                    selectedTextColor = IptvAccent,
                    unselectedIconColor = IptvOnSurface,
                    unselectedTextColor = IptvOnSurface,
                    indicatorColor = IptvAccent.copy(alpha = 0.15f),
                ),
            )
        }
    }
}

@Composable
private fun SideNavigationRail(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        containerColor = IptvSurface,
        modifier = modifier,
    ) {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                selected = index == selectedIndex,
                onClick = { onItemSelected(index) },
                icon = { Text(item.icon, fontSize = 22.sp) },
                label = { Text(item.label, fontSize = 11.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = IptvAccent,
                    selectedTextColor = IptvAccent,
                    unselectedIconColor = IptvOnSurface,
                    unselectedTextColor = IptvOnSurface,
                    indicatorColor = IptvAccent.copy(alpha = 0.15f),
                ),
            )
        }
    }
}

data class NavItem(
    val icon: String,
    val label: String,
    val route: String,
)

@Composable
fun AdaptiveContentPadding(
    hasNavigationRail: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val isCompact = FormFactorDetector.current == FormFactor.MOBILE

    if (isCompact) {
        Scaffold(
            bottomBar = { Spacer(modifier = Modifier.height(0.dp)) },
            containerColor = Color.Transparent,
        ) { paddingValues ->
            content(paddingValues)
        }
    } else {
        content(PaddingValues(0.dp))
    }
}

fun adaptiveCardWidth(): Dp {
    return when (FormFactorDetector.current) {
        FormFactor.MOBILE -> 130.dp
        FormFactor.DESKTOP -> 160.dp
        FormFactor.TV -> 180.dp
    }
}

fun adaptiveGridColumns(): Int {
    return when (FormFactorDetector.current) {
        FormFactor.MOBILE -> 3
        FormFactor.DESKTOP -> 5
        FormFactor.TV -> 6
    }
}
