package caib.charge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag

/** Stable product tabs shown around the central entry action. */
enum class SakuBottomTab(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
) {
    DETAIL("明细", Icons.AutoMirrored.Rounded.ListAlt, "navigation_明细"),
    CHART("图表", Icons.Rounded.BarChart, "navigation_图表"),
    DISCOVER("发现", Icons.Rounded.Explore, "navigation_发现"),
    PROFILE("我的", Icons.Rounded.Person, "navigation_我的"),
}

/**
 * Saku root navigation: two tabs, a warm entry action, then two tabs.
 *
 * The visible bar is exactly 80dp high. System navigation-bar space is added below it,
 * inside the same surface, so the host does not need to add another navigation inset.
 */
@Composable
fun SakuBottomBar(
    selectedTab: SakuBottomTab,
    onTabSelected: (SakuBottomTab) -> Unit,
    onEntryClick: () -> Unit,
    modifier: Modifier = Modifier,
    entryEnabled: Boolean = true,
) {
    val topShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
    Surface(
        modifier = modifier.fillMaxWidth().testTag("saku_bottom_bar"),
        shape = topShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SakuBottomTabItem(SakuBottomTab.DETAIL, selectedTab, onTabSelected, Modifier.weight(1f))
                SakuBottomTabItem(SakuBottomTab.CHART, selectedTab, onTabSelected, Modifier.weight(1f))
                EntryAction(onEntryClick, entryEnabled)
                SakuBottomTabItem(SakuBottomTab.DISCOVER, selectedTab, onTabSelected, Modifier.weight(1f))
                SakuBottomTabItem(SakuBottomTab.PROFILE, selectedTab, onTabSelected, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SakuBottomTabItem(
    tab: SakuBottomTab,
    selectedTab: SakuBottomTab,
    onTabSelected: (SakuBottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = tab == selectedTab
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .testTag(tab.testTag)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = { onTabSelected(tab) },
            )
            .semantics { contentDescription = "导航：${tab.label}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = foreground,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EntryAction(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = if (pressed) {
            tween(EntryMotionTokens.PressMillis)
        } else {
            spring(
                dampingRatio = 0.64f,
                stiffness = Spring.StiffnessHigh,
            )
        },
        label = "entry_fab_press",
    )
    Box(
        modifier = Modifier.width(64.dp).fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .testTag("entry_fab")
                .semantics {
                    role = Role.Button
                    contentDescription = "记一笔"
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
