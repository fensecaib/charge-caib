package caib.charge.ui.components

import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import caib.charge.ui.theme.caibExpenseContent

/** The primary accent is a soft fill, so ordinary text actions use the content role. */
@Composable
internal fun CaibTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = colors, contentPadding = contentPadding) {
        // Do not carry a parent's explicit text color over this control's state color.
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelLarge) { content() }
    }
}

/** Brand accents are fills; editable content and errors need readable foreground roles. */
@Composable
internal fun caibOutlinedTextFieldColors(containerColor: Color = Color.Transparent) =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        errorTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = containerColor,
        unfocusedContainerColor = containerColor,
        errorContainerColor = containerColor,
        disabledContainerColor = containerColor,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedPrefixColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPrefixColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorPrefixColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = MaterialTheme.colorScheme.onSurface,
        selectionColors = TextSelectionColors(
            handleColor = MaterialTheme.colorScheme.onSurface,
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        errorBorderColor = MaterialTheme.colorScheme.caibExpenseContent,
        errorLabelColor = MaterialTheme.colorScheme.caibExpenseContent,
        errorSupportingTextColor = MaterialTheme.colorScheme.caibExpenseContent,
        errorCursorColor = MaterialTheme.colorScheme.caibExpenseContent,
        errorLeadingIconColor = MaterialTheme.colorScheme.caibExpenseContent,
        errorTrailingIconColor = MaterialTheme.colorScheme.caibExpenseContent,
        // Disabled foregrounds retain Material's roles. Callers own the container surface.
    )
