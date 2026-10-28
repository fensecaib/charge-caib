package caib.charge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import caib.charge.ui.theme.controlOutlineColor
import caib.charge.ui.theme.caibIsDark
import caib.charge.ui.theme.mutedColor

private val SakuCorner = RoundedCornerShape(10.dp)

/** Success, errors and undo share the same quiet surface without changing message lifecycles. */
@Composable
fun CaibSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        if (colors.caibIsDark) {
            Snackbar(
                snackbarData = data,
                modifier = Modifier.border(1.dp, colors.outline, SnackbarDefaults.shape),
                containerColor = colors.surfaceVariant,
                contentColor = colors.onSurface,
                actionColor = colors.primary,
                dismissActionContentColor = colors.onSurfaceVariant,
            )
        } else {
            Snackbar(snackbarData = data)
        }
    }
}

/** Saku surface: semantic surface, a quiet outline, and only a one-dp lift. */
@Composable
fun SakuCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = if (onClick != null) controlOutlineColor() else MaterialTheme.colorScheme.outlineVariant,
    content: @Composable () -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, borderColor)
    val elevation = CardDefaults.cardElevation(
        defaultElevation = 1.dp,
        pressedElevation = 2.dp,
        focusedElevation = 2.dp
    )
    if (onClick == null) {
        Card(
            modifier = modifier,
            shape = SakuCorner,
            colors = colors,
            border = border,
            elevation = elevation
        ) { content() }
    } else {
        Card(
            modifier = modifier,
            onClick = onClick,
            enabled = enabled,
            shape = SakuCorner,
            colors = colors,
            border = border,
            elevation = elevation
        ) { content() }
    }
}

/** Exactly 48dp high, excluding any status-bar inset supplied by the route host. */
@Composable
fun SakuTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.width(48.dp).height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 16.dp else 4.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = if (subtitle == null) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp)
                },
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

/** A compact single-choice control whose visual and semantic targets stay 48dp high. */
@Composable
fun <T> SakuSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    itemContentDescription: ((T) -> String)? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(SakuCorner)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, controlOutlineColor(), SakuCorner),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics {
                        role = Role.Button
                        selected = isSelected
                        itemContentDescription?.let { contentDescription = it(item) }
                    }
                    .clickable(enabled = enabled) { onItemSelected(item) }
                    .padding(3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .then(
                        if (isSelected && MaterialTheme.colorScheme.caibIsDark) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        } else Modifier,
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = itemLabel(item),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Warm-yellow Saku commit action. */
@Composable
fun SakuCtaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = SakuCorner,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = mutedColor(lightAlpha = 0.55f)
        ),
        content = content
    )
}

/** Compatibility surface for existing Chart/Profile call sites. */
@Composable
fun CaibCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) = SakuCard(modifier = modifier, onClick = onClick, content = content)

/** Compatibility button; existing call sites keep their previous cyan primary semantics. */
@Composable
fun CaibPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = SakuCorner,
        colors = colors,
        content = content
    )
}
