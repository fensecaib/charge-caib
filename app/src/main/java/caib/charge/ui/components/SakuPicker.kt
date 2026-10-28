package caib.charge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import caib.charge.domain.model.EntryDateLimits
import java.time.LocalDate
import java.time.YearMonth

/** Shared Saku number column used by the entry date and detail month pickers. */
@Composable
fun SakuNumberPickerColumn(
    label: String,
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0),
    )

    LaunchedEffect(values, selected) {
        val nextIndex = values.indexOf(selected).coerceAtLeast(0)
        state.scrollToItem((nextIndex - 2).coerceAtLeast(0))
    }

    Column(modifier = modifier.fillMaxHeight()) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(top = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(values, key = { "$label-$it" }) { value ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        )
                        .semantics {
                            role = Role.Button
                            contentDescription = "$label：$value"
                            this.selected = isSelected
                        }
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = value.toString(),
                        style = if (isSelected) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun SakuMonthPickerDialog(
    initialMonth: YearMonth,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
) {
    val firstMonth = YearMonth.from(EntryDateLimits.MIN_DATE)
    val lastMonth = YearMonth.from(today)
    val safeInitial = initialMonth.coerceIn(firstMonth, lastMonth)
    var selectedYear by remember(safeInitial, lastMonth) { mutableIntStateOf(safeInitial.year) }
    var selectedMonth by remember(safeInitial, lastMonth) { mutableIntStateOf(safeInitial.monthValue) }
    val years = remember(lastMonth.year) { (firstMonth.year..lastMonth.year).toList() }
    val months = remember(selectedYear, lastMonth) {
        (1..if (selectedYear == lastMonth.year) lastMonth.monthValue else 12).toList()
    }

    LaunchedEffect(months) {
        selectedMonth = selectedMonth.coerceAtMost(months.last())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(10.dp),
        title = { Text("选择月份") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().height(248.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SakuNumberPickerColumn(
                    label = "年",
                    values = years,
                    selected = selectedYear,
                    onSelect = { year ->
                        selectedYear = year
                        selectedMonth = selectedMonth.coerceAtMost(
                            if (year == lastMonth.year) lastMonth.monthValue else 12,
                        )
                    },
                    modifier = Modifier.weight(1.2f),
                )
                SakuNumberPickerColumn(
                    label = "月",
                    values = months,
                    selected = selectedMonth,
                    onSelect = { selectedMonth = it },
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            CaibTextButton(onClick = { onConfirm(YearMonth.of(selectedYear, selectedMonth)) }) {
                Text("确定")
            }
        },
        dismissButton = {
            CaibTextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
