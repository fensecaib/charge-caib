package caib.charge.ui.entry

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import caib.charge.domain.AmountError
import caib.charge.ui.theme.expenseContentColor

/** Keep the entered expression available for correction, including its unfinished operand. */
@Composable
internal fun EntryAmountExpression(
    expression: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val scrollState = rememberScrollState()
    // New keys reveal the tail; the user can still scroll back to inspect earlier operands.
    LaunchedEffect(expression, scrollState.maxValue) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Text(
        expression.ifBlank { "0" },
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .testTag("entry_amount_expression")
            .semantics {
                contentDescription = "金额表达式：${expression.ifBlank { "未填写" }}"
            },
        textAlign = textAlign,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
    )
}

/** A reserved status line keeps the keypad in place when an expression becomes incomplete. */
@Composable
internal fun EntryAmountStatus(
    evaluationError: AmountError?,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val message = amountErrorMessage(evaluationError)
    val hasBlockingError = evaluationError != null && evaluationError != AmountError.EMPTY
    Text(
        message,
        modifier = modifier
            .fillMaxWidth()
            .testTag("entry_amount_status")
            .semantics {
                if (hasBlockingError) error(message)
            },
        textAlign = textAlign,
        style = MaterialTheme.typography.labelSmall,
        color = if (hasBlockingError) expenseContentColor()
            else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}
