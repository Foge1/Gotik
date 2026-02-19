package com.loaderapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BottomNavItem(
    val icon: ImageVector,
    val label: String,
    val badgeCount: Int = 0
)

@Composable
fun AppBottomBar(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 0.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            items.forEachIndexed { index, item ->
                AppBottomBarItem(
                    item = item,
                    isSelected = index == selectedIndex,
                    onClick = { onItemSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AppBottomBarItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else Color(0xFFAAAAAA),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else Color(0xFFAAAAAA),
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "textColor"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Тонкая линия-индикатор сверху — видна только у активного пункта
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(2.5.dp)
                .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else Color.Transparent
                )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Иконка
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Подпись
        Text(
            text = item.label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = textColor,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}
