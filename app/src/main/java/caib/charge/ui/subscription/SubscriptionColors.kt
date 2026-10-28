package caib.charge.ui.subscription

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import caib.charge.ui.components.caibOutlinedTextFieldColors
import caib.charge.ui.theme.caibExpenseContent
import caib.charge.ui.theme.caibIsDark

/** Caib's primary/error roles are soft fills in light mode, not small-text colors. */
@Composable
internal fun subscriptionErrorContentColor() = MaterialTheme.colorScheme.caibExpenseContent

@Composable
internal fun subscriptionFieldContainerColor() = MaterialTheme.colorScheme.let {
    if (it.caibIsDark) it.surfaceVariant else it.surfaceContainerLow
}

@Composable
internal fun subscriptionOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    containerColor = subscriptionFieldContainerColor(),
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContainerColor = subscriptionFieldContainerColor(),
)

@Composable
internal fun subscriptionFieldBorder(enabled: Boolean) = if (enabled) {
    BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
} else ButtonDefaults.outlinedButtonBorder(false)

@Composable
internal fun subscriptionInputColors() = caibOutlinedTextFieldColors(subscriptionFieldContainerColor())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun subscriptionDatePickerColors() = DatePickerDefaults.colors(
    currentYearContentColor = MaterialTheme.colorScheme.onSurface,
    todayContentColor = MaterialTheme.colorScheme.onSurface,
    todayDateBorderColor = MaterialTheme.colorScheme.outline,
    selectedDayContainerColor = MaterialTheme.colorScheme.inverseSurface,
    selectedDayContentColor = MaterialTheme.colorScheme.inverseOnSurface,
    selectedYearContainerColor = MaterialTheme.colorScheme.inverseSurface,
    selectedYearContentColor = MaterialTheme.colorScheme.inverseOnSurface,
    dateTextFieldColors = subscriptionInputColors(),
    // Retain M3's disabled roles: inactive dates/fields are exempt from active-content contrast.
)
