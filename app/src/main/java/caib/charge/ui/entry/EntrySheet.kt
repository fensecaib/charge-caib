package caib.charge.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import caib.charge.domain.AmountError
import caib.charge.domain.AmountExpressionEngine
import caib.charge.domain.model.EntryDateLimits
import caib.charge.ui.components.CategoryIcon
import caib.charge.ui.components.SakuCard
import caib.charge.ui.components.SakuCtaButton
import caib.charge.ui.components.SakuNumberPickerColumn
import caib.charge.ui.components.SakuTopBar
import caib.charge.ui.components.EntryMotionTokens
import caib.charge.ui.model.UiCategory
import caib.charge.ui.model.UiEntryType
import caib.charge.ui.model.UiLedgerEntry
import caib.charge.ui.model.UiRecentNoteState
import caib.charge.ui.model.UiRecentNoteSuggestion
import caib.charge.ui.theme.CategoryPalette
import caib.charge.ui.util.formatFullDate
import caib.charge.ui.util.formatMoney
import java.time.LocalDate
import java.time.YearMonth

enum class EntryEditorMode {
    NEW,
    FULL,
    CATEGORY_ONLY,
    NOTE_ONLY,
    AMOUNT_DATE_ONLY
}

data class EntrySheetResult(
    val id: Long?,
    val type: UiEntryType,
    val categoryId: String,
    val amountCents: Long,
    val date: LocalDate,
    val note: String,
    val originalDate: LocalDate? = null,
)

/**
 * Full-screen-ready entry editor. Its route host owns status/navigation-bar insets; the editor
 * itself guarantees a 48dp top bar and keeps the 4x4 calculator fixed while the system IME is
 * hidden. The focused upper content remains scrollable on compact portrait screens.
 */
@Composable
fun EntryEditorScreen(
    existing: UiLedgerEntry?,
    categories: List<UiCategory>,
    recentNoteState: UiRecentNoteState,
    onRecentNoteContextChanged: (UiEntryType, String?) -> Unit,
    onManageCategories: (UiEntryType) -> Unit,
    onDismiss: () -> Unit,
    onSave: (EntrySheetResult) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    isSaving: Boolean = false,
    mode: EntryEditorMode = if (existing == null) EntryEditorMode.NEW else EntryEditorMode.FULL,
    initialCategoryId: String? = null,
    preferredCategoryIds: Map<UiEntryType, String> = emptyMap(),
    onDelete: ((UiLedgerEntry) -> Unit)? = null,
    motionPreview: Boolean = false,
) {
    val initialType = existing?.type ?: UiEntryType.EXPENSE
    val initialCategory = existing?.categoryId ?: defaultCategoryId(
        categories = categories,
        type = initialType,
        preferredId = preferredCategoryIds[initialType] ?: initialCategoryId
    )
    val initialExpression = existing?.amountCents?.let(::centsToExpression).orEmpty()
    val initialDate = existing?.date ?: today
    val initialNote = existing?.note.orEmpty()

    var type by rememberSaveable(existing?.id, mode) {
        mutableStateOf(initialType)
    }
    var categoryId by rememberSaveable(existing?.id, mode) {
        mutableStateOf(initialCategory)
    }
    var expression by rememberSaveable(existing?.id, mode) {
        mutableStateOf(initialExpression)
    }
    var dateEpochDay by rememberSaveable(existing?.id, mode, today) {
        mutableLongStateOf(initialDate.toEpochDay())
    }
    val date = LocalDate.ofEpochDay(dateEpochDay)
    var note by rememberSaveable(existing?.id, mode) {
        mutableStateOf(initialNote)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var noteFocused by remember(existing?.id, mode) { mutableStateOf(false) }
    var recentNotesDismissed by remember(existing?.id, mode, type, categoryId) {
        mutableStateOf(false)
    }
    var editorTopPx by remember { mutableIntStateOf(0) }

    val evaluation = remember(expression) { AmountExpressionEngine.evaluate(expression) }
    val activeCategories = remember(categories, type) {
        categories.filter { it.type == type && !it.archived }.sortedBy { it.sortOrder }
    }
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val visibleRecentNotes = recentNoteState.itemsFor(type, categoryId)
    val currentRecentNoteContextChanged = rememberUpdatedState(onRecentNoteContextChanged)
    val noteFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeVisible by remember(density, imeInsets) {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }

    val showType = mode == EntryEditorMode.NEW || mode == EntryEditorMode.FULL
    val showCategories = mode == EntryEditorMode.NEW ||
        mode == EntryEditorMode.FULL ||
        mode == EntryEditorMode.CATEGORY_ONLY
    val showAmount = mode == EntryEditorMode.NEW ||
        mode == EntryEditorMode.FULL ||
        mode == EntryEditorMode.AMOUNT_DATE_ONLY
    val showNote = mode == EntryEditorMode.NEW ||
        mode == EntryEditorMode.FULL ||
        mode == EntryEditorMode.NOTE_ONLY
    val supportsRecentNotes = mode == EntryEditorMode.NEW || mode == EntryEditorMode.FULL
    val showKeypad = showAmount && !noteFocused && !imeVisible
    val showRecentNotePopup = supportsRecentNotes && noteFocused &&
        !recentNotesDismissed && visibleRecentNotes.isNotEmpty()

    fun submit(categoryOverride: String? = null) {
        val selectedId = categoryOverride ?: categoryId ?: return
        val cents = evaluation.resultCents ?: return
        if (isSaving) return
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onSave(
            EntrySheetResult(
                id = existing?.id,
                type = type,
                categoryId = selectedId,
                amountCents = cents,
                date = date,
                note = note.trim(),
                originalDate = existing?.date,
            )
        )
    }

    val canSave = categoryId != null && evaluation.isValid && !isSaving
    val isDirty = type != initialType ||
        categoryId != initialCategory ||
        expression != initialExpression ||
        date != initialDate ||
        note != initialNote

    fun completeDismiss() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onDismiss()
    }

    fun requestDismiss() {
        when {
            isSaving -> Unit
            isDirty -> showDiscardConfirm = true
            else -> completeDismiss()
        }
    }

    LaunchedEffect(noteFocused, type, categoryId, supportsRecentNotes, motionPreview) {
        if (!motionPreview) {
            onRecentNoteContextChanged(
                type,
                categoryId.takeIf { supportsRecentNotes && noteFocused },
            )
        }
    }
    DisposableEffect(motionPreview) {
        onDispose {
            if (!motionPreview) {
                currentRecentNoteContextChanged.value(UiEntryType.EXPENSE, null)
            }
        }
    }
    LaunchedEffect(categories, type, mode, initialCategoryId, preferredCategoryIds) {
        if (mode == EntryEditorMode.NEW && categories.none { it.id == categoryId && it.type == type && !it.archived }) {
            categoryId = defaultCategoryId(categories, type, preferredCategoryIds[type] ?: initialCategoryId)
        }
    }
    LaunchedEffect(mode) {
        if (!motionPreview && mode == EntryEditorMode.NOTE_ONLY) noteFocusRequester.requestFocus()
    }
    BackHandler(enabled = !motionPreview) {
        requestDismiss()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("entry_editor_screen")
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { editorTopPx = it.boundsInWindow().top.toInt() }
            .imePadding()
    ) {
        if (showType) {
            EntryTypeTopBar(
                title = if (mode == EntryEditorMode.NEW) "新增记账" else "编辑账目",
                selected = type,
                enabled = !isSaving,
                onBack = ::requestDismiss,
                onTypeSelected = { nextType ->
                    if (type != nextType) {
                        recentNotesDismissed = true
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        type = nextType
                        categoryId = defaultCategoryId(
                            categories,
                            nextType,
                            preferredCategoryIds[nextType] ?: initialCategoryId,
                        )
                    }
                },
            )
        } else {
            SakuTopBar(
                title = when (mode) {
                    EntryEditorMode.CATEGORY_ONLY -> "修改分类"
                    EntryEditorMode.NOTE_ONLY -> "修改备注"
                    EntryEditorMode.AMOUNT_DATE_ONLY -> "修改金额与日期"
                    EntryEditorMode.NEW -> "新增记账"
                    EntryEditorMode.FULL -> "编辑账目"
                },
                onBack = ::requestDismiss,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!showCategories && selectedCategory != null) {
                    EntryContextStrip(
                        category = selectedCategory,
                        amountCents = evaluation.resultCents ?: existing?.amountCents
                    )
                }

                if (showCategories) {
                    SectionHeading(
                        title = "选择分类",
                        supporting = when {
                            mode == EntryEditorMode.CATEGORY_ONLY -> "选择后立即保存"
                            selectedCategory == null -> "先选分类，再填写金额"
                            else -> "已选 ${selectedCategory.name}"
                        }
                    )
                    EntryCategoryGrid(
                        categories = activeCategories,
                        selectedId = categoryId,
                        enabled = !isSaving,
                        onSelect = { category ->
                            recentNotesDismissed = true
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            categoryId = category.id
                            if (mode == EntryEditorMode.CATEGORY_ONLY) submit(category.id)
                        },
                        onManage = { onManageCategories(type) }
                    )
                }

                if (showAmount && !showKeypad) {
                    AmountAndDateCard(
                        categoryName = selectedCategory?.name,
                        expression = expression,
                        evaluationResult = evaluation.resultCents,
                        evaluationError = evaluation.error,
                        date = date,
                        enabled = !isSaving,
                        onDateClick = { showDatePicker = true }
                    )
                }

                if (showNote) {
                    RecentNoteField(
                        note = note,
                        onNoteChange = { note = it.take(100) },
                        enabled = !isSaving,
                        noteFocusRequester = noteFocusRequester,
                        onFocusChanged = { focused ->
                            if (focused && !noteFocused) recentNotesDismissed = false
                            noteFocused = focused
                        },
                        showPopup = showRecentNotePopup,
                        suggestions = visibleRecentNotes,
                        editorTopPx = editorTopPx,
                        onDismissPopup = { recentNotesDismissed = true },
                        onSuggestionClick = { suggestion ->
                            note = suggestion.note.take(100)
                            recentNotesDismissed = true
                        },
                    )
                }

                if (existing != null && mode == EntryEditorMode.FULL && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !isSaving,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("删除")
                    }
                }
            }

            when {
                showKeypad -> Column {
                    CompactAmountAndDateBar(
                        categoryName = selectedCategory?.name,
                        expression = expression,
                        evaluationResult = evaluation.resultCents,
                        evaluationError = evaluation.error,
                        date = date,
                        enabled = !isSaving,
                        onDateClick = { showDatePicker = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    EntryKeypad(
                        expression = expression,
                        enabled = !isSaving,
                        canSave = canSave,
                        isSaving = isSaving,
                        onExpressionChange = { expression = it },
                        onDateClick = { showDatePicker = true },
                        onSave = { submit() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                mode != EntryEditorMode.CATEGORY_ONLY -> SakuCtaButton(
                    onClick = { submit() },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                        .testTag("entry_save")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    } else {
                        Text("完成")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        SakuDatePickerDialog(
            initialDate = date,
            today = today,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                dateEpochDay = it.toEpochDay()
                showDatePicker = false
            }
        )
    }

    if (showDeleteConfirm && existing != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(10.dp),
            title = { Text("删除这笔账目？") },
            text = { Text("删除后可在底部提示中立即撤销。") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("confirm_delete_entry"),
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(existing)
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            shape = RoundedCornerShape(10.dp),
            title = { Text("放弃修改？") },
            text = { Text("尚未保存的内容会丢失。") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("confirm_discard_entry"),
                    enabled = !isSaving,
                    onClick = {
                        showDiscardConfirm = false
                        completeDismiss()
                    },
                ) { Text("放弃", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("继续编辑") }
            }
        )
    }
}

@Composable
private fun EntryTypeTopBar(
    title: String,
    selected: UiEntryType,
    enabled: Boolean,
    onBack: () -> Unit,
    onTypeSelected: (UiEntryType) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.background)
            .semantics {
                heading()
                contentDescription = title
            },
    ) {
        IconButton(
            onClick = onBack,
            enabled = enabled,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .semantics { contentDescription = "返回" },
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
        }

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .width(144.dp)
                .height(36.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {}

        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .width(144.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryTypeTopBarOption(
                label = "支出",
                type = UiEntryType.EXPENSE,
                selected = selected == UiEntryType.EXPENSE,
                enabled = enabled,
                onSelect = onTypeSelected,
                modifier = Modifier.weight(1f),
            )
            EntryTypeTopBarOption(
                label = "收入",
                type = UiEntryType.INCOME,
                selected = selected == UiEntryType.INCOME,
                enabled = enabled,
                onSelect = onTypeSelected,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EntryTypeTopBarOption(
    label: String,
    type: UiEntryType,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (UiEntryType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedContainer = if (type == UiEntryType.EXPENSE) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val selectedContent = if (type == UiEntryType.EXPENSE) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val selectedOutline = if (type == UiEntryType.EXPENSE) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "记$label"
                this.selected = selected
            }
            .clickable(enabled = enabled) { onSelect(type) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(2.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) selectedContainer else Color.Transparent)
                .then(
                    if (selected) {
                        Modifier.border(1.dp, selectedOutline, RoundedCornerShape(16.dp))
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) selectedContent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentNoteField(
    note: String,
    onNoteChange: (String) -> Unit,
    enabled: Boolean,
    noteFocusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    showPopup: Boolean,
    suggestions: List<UiRecentNoteSuggestion>,
    editorTopPx: Int,
    onDismissPopup: () -> Unit,
    onSuggestionClick: (UiRecentNoteSuggestion) -> Unit,
) {
    var fieldBounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val popupWidth = with(density) { fieldBounds.width.toDp() }
    val safeTopPx = editorTopPx + with(density) { 48.dp.roundToPx() }
    val popupGapPx = with(density) { 6.dp.roundToPx() }
    val availableHeight = with(density) {
        (fieldBounds.top.toInt() - safeTopPx - popupGapPx).coerceAtLeast(0).toDp()
    }
    val popupHeight = minOf(availableHeight, 192.dp)
    val popupIsVisible = showPopup && fieldBounds.width > 0f && popupHeight >= 48.dp

    BackHandler(enabled = popupIsVisible) {
        onDismissPopup()
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(noteFocusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .onGloballyPositioned { fieldBounds = it.boundsInWindow() }
                .testTag("entry_note"),
            enabled = enabled,
            label = { Text("备注") },
            supportingText = { Text("${note.length}/100") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
        )

        if (popupIsVisible) {
            Popup(
                popupPositionProvider = RecentNotePopupPositionProvider(
                    safeTopPx = safeTopPx,
                    gapPx = popupGapPx,
                ),
                onDismissRequest = onDismissPopup,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = true,
                    clippingEnabled = true,
                ),
            ) {
                Surface(
                    modifier = Modifier
                        .width(popupWidth)
                        .heightIn(max = popupHeight)
                        .testTag("recent_note_popup")
                        .semantics { contentDescription = "最近使用备注" },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 6.dp,
                ) {
                    LazyColumn {
                        items(
                            items = suggestions.take(10),
                            key = { it.normalizedNote },
                        ) { suggestion ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = "使用最近备注：${suggestion.note}"
                                    }
                                    .clickable { onSuggestionClick(suggestion) }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = suggestion.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class RecentNotePopupPositionProvider(
    private val safeTopPx: Int,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(
            0,
            (windowSize.width - popupContentSize.width).coerceAtLeast(0),
        )
        val preferredY = anchorBounds.top - gapPx - popupContentSize.height
        val y = preferredY.coerceAtLeast(safeTopPx).coerceAtMost(
            (windowSize.height - popupContentSize.height).coerceAtLeast(0),
        )
        return IntOffset(x, y)
    }
}

@Composable
private fun SectionHeading(title: String, supporting: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EntryCategoryGrid(
    categories: List<UiCategory>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (UiCategory) -> Unit,
    onManage: () -> Unit
) {
    val cells: List<UiCategory?> = categories + listOf(null)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(4).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCells.forEach { category ->
                    EntryCategoryTile(
                        name = category?.name ?: "设置",
                        iconKey = category?.iconKey,
                        colorKey = category?.colorKey ?: 0,
                        selected = category?.id == selectedId,
                        enabled = enabled,
                        onClick = {
                            if (category == null) onManage() else onSelect(category)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun EntryCategoryTile(
    name: String,
    iconKey: String?,
    colorKey: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SakuCard(
        modifier = modifier
            .height(76.dp)
            .semantics { this.selected = selected },
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        enabled = enabled,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (iconKey == null) MaterialTheme.colorScheme.surfaceVariant
                        else CategoryPalette[colorKey.mod(CategoryPalette.size)].copy(alpha = 0.22f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (iconKey == null) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                } else {
                    CategoryIcon(iconKey, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EntryContextStrip(category: UiCategory, amountCents: Long?) {
    SakuCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CategoryPalette[category.colorKey.mod(CategoryPalette.size)].copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                CategoryIcon(category.iconKey, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Text(category.name, modifier = Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.titleMedium)
            amountCents?.let {
                Text(formatMoney(it), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AmountAndDateCard(
    categoryName: String?,
    expression: String,
    evaluationResult: Long?,
    evaluationError: AmountError?,
    date: LocalDate,
    enabled: Boolean,
    onDateClick: () -> Unit
) {
    SakuCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    categoryName ?: "未选择分类",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (categoryName == null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = onDateClick, modifier = Modifier.height(48.dp), enabled = enabled) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(formatFullDate(date), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
            Text(
                expression.ifBlank { "0" },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            AnimatedContent(
                targetState = evaluationResult?.let(::formatMoney) ?: "¥0.00",
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
                transitionSpec = { entryAmountTransition() },
                label = "entry_amount_full",
            ) { amount ->
                Text(
                    amount,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (evaluationError != null && evaluationError != AmountError.EMPTY) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                amountErrorMessage(evaluationError),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall,
                color = if (evaluationError == null || evaluationError == AmountError.EMPTY) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun CompactAmountAndDateBar(
    categoryName: String?,
    expression: String,
    evaluationResult: Long?,
    evaluationError: AmountError?,
    date: LocalDate,
    enabled: Boolean,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasBlockingError = evaluationError != null && evaluationError != AmountError.EMPTY
    SakuCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = "金额${evaluationResult?.let(::formatMoney) ?: "未填写"}，日期${formatFullDate(date)}，点击修改日期"
            },
        onClick = onDateClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${categoryName ?: "未选择分类"} · ${date.monthValue}月${date.dayOfMonth}日",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (categoryName == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        hasBlockingError -> amountErrorMessage(evaluationError)
                        expression.isBlank() -> "请输入金额"
                        else -> expression
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasBlockingError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedContent(
                targetState = evaluationResult?.let(::formatMoney) ?: "¥0.00",
                modifier = Modifier.width(148.dp),
                contentAlignment = Alignment.CenterEnd,
                transitionSpec = { entryAmountTransition() },
                label = "entry_amount_compact",
            ) { amount ->
                Text(
                    amount,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (hasBlockingError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun entryAmountTransition() = ContentTransform(
    targetContentEnter = slideInVertically(
        animationSpec = tween(EntryMotionTokens.AmountMillis),
        initialOffsetY = { it / 4 },
    ) + fadeIn(tween(100)),
    initialContentExit = slideOutVertically(
        animationSpec = tween(EntryMotionTokens.AmountMillis),
        targetOffsetY = { -it / 4 },
    ) + fadeOut(tween(90)),
    sizeTransform = null,
)

private data class KeySpec(val label: String, val semanticLabel: String = label)

private val EntryKeyRows = listOf(
    listOf(KeySpec("7"), KeySpec("8"), KeySpec("9"), KeySpec("日期")),
    listOf(KeySpec("4"), KeySpec("5"), KeySpec("6"), KeySpec("+")),
    listOf(KeySpec("1"), KeySpec("2"), KeySpec("3"), KeySpec("-")),
    listOf(KeySpec("."), KeySpec("0"), KeySpec("⌫", "退格"), KeySpec("完成")),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EntryKeypad(
    expression: String,
    enabled: Boolean,
    canSave: Boolean,
    isSaving: Boolean,
    onExpressionChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentExpression = rememberUpdatedState(expression)
    val currentOnExpressionChange = rememberUpdatedState(onExpressionChange)
    val currentOnDateClick = rememberUpdatedState(onDateClick)
    val currentOnSave = rememberUpdatedState(onSave)
    val onKeyClick: (KeySpec) -> Unit = remember {
        { key ->
            when (key.label) {
                "⌫" -> currentOnExpressionChange.value(
                    AmountExpressionEngine.backspace(currentExpression.value)
                )
                "日期" -> currentOnDateClick.value()
                "完成" -> currentOnSave.value()
                else -> currentOnExpressionChange.value(
                    AmountExpressionEngine.append(currentExpression.value, key.label.single()).take(48)
                )
            }
        }
    }
    val onClear = remember { { currentOnExpressionChange.value("") } }
    SakuCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(226.dp)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EntryKeyRows.forEach { keys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    keys.forEach { key ->
                        val isSave = key.label == "完成"
                        val keyEnabled = if (isSave) canSave else enabled
                        EntryKeyButton(
                            key = key,
                            enabled = keyEnabled,
                            isSaving = isSave && isSaving,
                            onClick = onKeyClick,
                            onClear = onClear,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryKeyButton(
    key: KeySpec,
    enabled: Boolean,
    isSaving: Boolean,
    onClick: (KeySpec) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember(key.label) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val indication = LocalIndication.current
    val keyScale = animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = if (pressed) {
            tween(EntryMotionTokens.PressMillis)
        } else {
            spring(
                dampingRatio = 0.66f,
                stiffness = Spring.StiffnessHigh,
            )
        },
        label = "entry_key_${key.semanticLabel}_press",
    )
    val isSave = key.label == "完成"
    Box(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = keyScale.value
                scaleY = keyScale.value
            }
            .clip(RoundedCornerShape(10.dp))
            .background(keyBackground(key.label, enabled))
            .semantics {
                role = Role.Button
                contentDescription = when (key.label) {
                    "完成" -> "完成记账"
                    "日期" -> "选择日期"
                    "⌫" -> "数字键退格，长按清空"
                    else -> "数字键${key.semanticLabel}"
                }
            }
            .testTag(if (isSave) "entry_save" else "entry_key_${key.semanticLabel}")
            .then(
                if (key.label == "⌫") {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = indication,
                        onLongClickLabel = "清空金额",
                        onLongClick = onClear,
                        onClick = { onClick(key) },
                    )
                } else {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = indication,
                        onClick = { onClick(key) },
                    )
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isSave && isSaving -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onTertiary,
            )
            key.label == "⌫" -> Icon(
                Icons.AutoMirrored.Rounded.Backspace,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            else -> Text(
                key.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun keyBackground(label: String, enabled: Boolean): Color {
    if (!enabled) return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    return when (label) {
        "完成" -> MaterialTheme.colorScheme.tertiary
        "+", "-" -> MaterialTheme.colorScheme.primaryContainer
        "日期" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
internal fun SakuDatePickerDialog(
    initialDate: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val firstDate = EntryDateLimits.MIN_DATE
    val safeInitial = initialDate.coerceIn(firstDate, today)
    var selectedYear by remember(safeInitial, today) { mutableIntStateOf(safeInitial.year) }
    var selectedMonth by remember(safeInitial, today) { mutableIntStateOf(safeInitial.monthValue) }
    var selectedDay by remember(safeInitial, today) { mutableIntStateOf(safeInitial.dayOfMonth) }

    val years = remember(today.year) { (EntryDateLimits.MIN_DATE.year..today.year).toList() }
    val months = remember(selectedYear, today) {
        (1..maxMonthFor(selectedYear, today)).toList()
    }
    val days = remember(selectedYear, selectedMonth, today) {
        (1..maxDayFor(selectedYear, selectedMonth, today)).toList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(10.dp),
        title = { Text("选择日期") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().height(248.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SakuNumberPickerColumn(
                    label = "年",
                    values = years,
                    selected = selectedYear,
                    onSelect = { nextYear ->
                        selectedYear = nextYear
                        selectedMonth = selectedMonth.coerceAtMost(maxMonthFor(nextYear, today))
                        selectedDay = selectedDay.coerceAtMost(
                            maxDayFor(nextYear, selectedMonth, today)
                        )
                    },
                    modifier = Modifier.weight(1.25f)
                )
                SakuNumberPickerColumn(
                    label = "月",
                    values = months,
                    selected = selectedMonth,
                    onSelect = { nextMonth ->
                        selectedMonth = nextMonth
                        selectedDay = selectedDay.coerceAtMost(
                            maxDayFor(selectedYear, nextMonth, today)
                        )
                    },
                    modifier = Modifier.weight(0.9f)
                )
                SakuNumberPickerColumn(
                    label = "日",
                    values = days,
                    selected = selectedDay,
                    onSelect = { selectedDay = it },
                    modifier = Modifier.weight(0.9f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(clampedDate(selectedYear, selectedMonth, selectedDay, today))
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun defaultCategoryId(
    categories: List<UiCategory>,
    type: UiEntryType,
    preferredId: String?
): String? {
    val active = categories.filter { it.type == type && !it.archived }.sortedBy { it.sortOrder }
    return active.firstOrNull { it.id == preferredId }?.id
        ?: active.firstOrNull {
            it.name == if (type == UiEntryType.EXPENSE) "餐饮" else "工资"
        }?.id
        ?: active.firstOrNull()?.id
}

private fun maxMonthFor(year: Int, today: LocalDate): Int =
    if (year == today.year) today.monthValue else 12

private fun maxDayFor(year: Int, month: Int, today: LocalDate): Int {
    val monthLength = YearMonth.of(year, month).lengthOfMonth()
    return if (year == today.year && month == today.monthValue) {
        minOf(monthLength, today.dayOfMonth)
    } else {
        monthLength
    }
}

private fun clampedDate(year: Int, month: Int, day: Int, today: LocalDate): LocalDate {
    val safeMonth = month.coerceIn(1, maxMonthFor(year, today))
    val safeDay = day.coerceIn(1, maxDayFor(year, safeMonth, today))
    return LocalDate.of(
        year.coerceIn(EntryDateLimits.MIN_DATE.year, today.year),
        safeMonth,
        safeDay,
    ).coerceAtMost(today)
}
