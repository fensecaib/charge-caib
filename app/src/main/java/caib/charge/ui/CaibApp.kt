package caib.charge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import caib.charge.ui.chart.CategoryAnalysisScreen
import caib.charge.ui.chart.ChartScreen
import caib.charge.ui.chart.ChartViewModel
import caib.charge.ui.components.EntryLaunchOverlay
import caib.charge.ui.components.EntryReturnOverlay
import caib.charge.ui.components.EntrySaveSuccessOverlay
import caib.charge.ui.components.SakuBottomBar
import caib.charge.ui.components.SakuBottomTab
import caib.charge.ui.components.SakuMonthPickerDialog
import caib.charge.ui.components.rememberEntryReturnMotionState
import caib.charge.ui.detail.DetailScreen
import caib.charge.ui.discover.DiscoverScreen
import caib.charge.ui.entry.EntryEditorMode
import caib.charge.ui.entry.EntryEditorScreen
import caib.charge.ui.entry.QuickAmountEditorOverlay
import caib.charge.ui.entry.QuickNoteEditorDialog
import caib.charge.ui.model.UiChartPeriod
import caib.charge.ui.model.UiEntryType
import caib.charge.ui.model.UiLedgerEntry
import caib.charge.ui.model.UiRecentCategoriesState
import caib.charge.ui.model.UiRecentNoteState
import caib.charge.ui.navigation.KeepAliveTabs
import caib.charge.ui.navigation.entries
import caib.charge.ui.navigation.rememberTopLevelNavigation
import caib.charge.ui.profile.ProfileScreen
import caib.charge.ui.settings.CategoryManagerSheet
import caib.charge.ui.settings.ImportPreviewSheet
import caib.charge.domain.model.CaibThemeMode
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object DetailKey : NavKey
@Serializable data object ChartKey : NavKey
@Serializable data object DiscoverKey : NavKey
@Serializable data object ProfileKey : NavKey

@Serializable
data class EntryEditorKey(
    val entryId: Long? = null,
    val mode: String = EntryEditorMode.NEW.name,
) : NavKey

@Serializable
data class CategoryAnalysisKey(
    val categoryId: String,
    val type: String,
    val period: String,
    val anchorEpochDay: Long,
) : NavKey

private data class Destination(
    val key: NavKey,
    val tab: SakuBottomTab,
)

private data class PendingEntrySaveMotion(
    val sessionId: Int,
    val savedId: Long,
    val entryDate: LocalDate,
    val editorKey: EntryEditorKey,
)

private data class PendingEntryReturnMotion(
    val sessionId: Int,
    val editorKey: EntryEditorKey,
)

private val destinations = listOf(
    Destination(DetailKey, SakuBottomTab.DETAIL),
    Destination(ChartKey, SakuBottomTab.CHART),
    Destination(DiscoverKey, SakuBottomTab.DISCOVER),
    Destination(ProfileKey, SakuBottomTab.PROFILE),
)

@Composable
fun CaibApp(
    viewModel: CaibViewModel = hiltViewModel(),
    chartViewModel: ChartViewModel = hiltViewModel(),
    themeMode: CaibThemeMode = CaibThemeMode.SYSTEM,
    onThemeModeChange: (CaibThemeMode) -> Unit = {},
) {
    val navigation = rememberTopLevelNavigation(DetailKey, destinations.mapTo(linkedSetOf()) { it.key })
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val recentNoteState by viewModel.recentNoteState.collectAsStateWithLifecycle()
    val importBatch by viewModel.importPreview.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveConfirmation by viewModel.entrySaveFeedback.confirmation.collectAsStateWithLifecycle()
    val recentCategoriesState by viewModel.recentCategoriesState.collectAsStateWithLifecycle()
    val recentCategories = (recentCategoriesState as? UiRecentCategoriesState.Ready)?.categoryIds.orEmpty()
    val newEntrySeedReady = recentCategoriesState is UiRecentCategoriesState.Ready &&
        UiEntryType.entries.all { type -> categories.any { it.type == type && !it.archived } }

    var categoryManagerType by remember { mutableStateOf<UiEntryType?>(null) }
    var quickAmountTarget by remember { mutableStateOf<Pair<UiLedgerEntry, Rect>?>(null) }
    var quickNoteEntry by remember { mutableStateOf<UiLedgerEntry?>(null) }
    var detailMonthPickerInitial by remember { mutableStateOf<YearMonth?>(null) }
    var motionSession by remember { mutableIntStateOf(0) }
    var entryLaunchSession by remember { mutableStateOf<Int?>(null) }
    var entryDestinationReadySession by remember { mutableStateOf<Int?>(null) }
    var pendingEntrySaveMotion by remember { mutableStateOf<PendingEntrySaveMotion?>(null) }
    var pendingEntryReturnMotion by remember { mutableStateOf<PendingEntryReturnMotion?>(null) }
    var entryReturnRootReadySession by remember { mutableStateOf<Int?>(null) }
    var entryReturnBottomBarReadySession by remember { mutableStateOf<Int?>(null) }
    var recentlyCreatedEntryId by remember { mutableStateOf<Long?>(null) }
    val entryReturnMotionState = rememberEntryReturnMotionState(pendingEntryReturnMotion?.sessionId)
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeHidden by remember(density, imeInsets) {
        derivedStateOf { imeInsets.getBottom(density) == 0 }
    }
    val snackbar = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        chartViewModel.refreshToday()
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            viewModel.entrySaveFeedback.beginInteraction()
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message.text, message.actionLabel)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) message.action?.invoke()
        }
    }

    LaunchedEffect(recentlyCreatedEntryId) {
        val entryId = recentlyCreatedEntryId ?: return@LaunchedEffect
        delay(2_000)
        if (recentlyCreatedEntryId == entryId) recentlyCreatedEntryId = null
    }

    fun openEditor(entryId: Long?, mode: EntryEditorMode) {
        val key = EntryEditorKey(entryId, mode.name)
        if (navigation.activeStack.lastOrNull() == key) return
        viewModel.entrySaveFeedback.beginInteraction()
        viewModel.prepareEditor(entryId, force = true)
        navigation.push(key)
    }

    fun launchNewEntry() {
        if (
            !newEntrySeedReady ||
            entryLaunchSession != null ||
            pendingEntrySaveMotion != null ||
            pendingEntryReturnMotion != null
        ) return
        viewModel.entrySaveFeedback.beginInteraction()
        viewModel.prepareEditor(null, force = true)
        motionSession += 1
        entryDestinationReadySession = null
        entryLaunchSession = motionSession
    }

    fun dismissEditor(key: EntryEditorKey) {
        if (navigation.activeStack.lastOrNull() != key) return
        if (viewModel.isSaving.value) return
        val mode = runCatching { EntryEditorMode.valueOf(key.mode) }.getOrDefault(EntryEditorMode.FULL)
        if (key.entryId != null || mode != EntryEditorMode.NEW) {
            navigation.pop()
            viewModel.clearRecentNoteContext()
            return
        }
        if (entryLaunchSession != null || pendingEntrySaveMotion != null || pendingEntryReturnMotion != null) return
        motionSession += 1
        entryReturnRootReadySession = null
        entryReturnBottomBarReadySession = null
        pendingEntryReturnMotion = PendingEntryReturnMotion(
            sessionId = motionSession,
            editorKey = key,
        )
    }

    val provider = entryProvider {
        entry<DetailKey> {
            val filter by viewModel.currentFilter.collectAsStateWithLifecycle()
            val entries by viewModel.entries.collectAsStateWithLifecycle()
            val summary by viewModel.summary.collectAsStateWithLifecycle()
            DetailScreen(
                filter = filter,
                entries = entries,
                categories = categories,
                summary = summary,
                today = viewModel.currentDate,
                onPreviousMonth = viewModel::previousDetailPeriod,
                onNextMonth = viewModel::nextDetailPeriod,
                onPeriodPickerRequest = { detailMonthPickerInitial = filter.month },
                onTypeFilter = viewModel::setTypeFilter,
                onCategoryFilter = viewModel::setCategoryFilter,
                onEntryClick = { openEditor(it.id, EntryEditorMode.FULL) },
                onCategoryHotspotClick = { openEditor(it.id, EntryEditorMode.CATEGORY_ONLY) },
                onInfoHotspotClick = {
                    viewModel.entrySaveFeedback.beginInteraction()
                    quickNoteEntry = it
                },
                onAmountHotspotClick = { entry, bounds ->
                    viewModel.entrySaveFeedback.beginInteraction()
                    quickAmountTarget = entry to bounds
                },
                onDeleteRequest = { entry -> viewModel.deleteEntry(entry.id) {} },
                recentlyCreatedEntryId = recentlyCreatedEntryId,
                onInsertAnimationFinished = { entryId ->
                    if (recentlyCreatedEntryId == entryId) recentlyCreatedEntryId = null
                },
            )
        }
        entry<ChartKey> {
            val chartType by chartViewModel.chartType.collectAsStateWithLifecycle()
            val chartKind by chartViewModel.chartPeriodKind.collectAsStateWithLifecycle()
            val chartLabel by chartViewModel.chartPeriodLabel.collectAsStateWithLifecycle()
            val chartPoints by chartViewModel.chartPoints.collectAsStateWithLifecycle()
            val ranking by chartViewModel.chartRanking.collectAsStateWithLifecycle()
            val chartAnchor by chartViewModel.chartPeriodAnchor.collectAsStateWithLifecycle()
            val chartToday by chartViewModel.currentDate.collectAsStateWithLifecycle()
            val selectedPoint by chartViewModel.selectedPoint.collectAsStateWithLifecycle()
            val selectedPointEntries by chartViewModel.selectedPointEntries.collectAsStateWithLifecycle()
            ChartScreen(
                type = chartType,
                period = chartKind,
                periodLabel = chartLabel,
                periodAnchorDate = chartAnchor,
                today = chartToday,
                points = chartPoints,
                ranking = ranking,
                selectedPoint = selectedPoint,
                selectedPointEntries = selectedPointEntries,
                onTypeChange = chartViewModel::setChartType,
                onPeriodChange = chartViewModel::setChartPeriod,
                onPeriodSelected = chartViewModel::selectChartAnchor,
                onPointClick = chartViewModel::selectPoint,
                onCategoryClick = categoryClick@{ slice ->
                    val key = chartViewModel.categoryAnalysisKey(slice) ?: return@categoryClick
                    chartViewModel.openCategoryAnalysis(
                        key.categoryId,
                        key.type,
                        key.period,
                        key.anchorDate,
                    )
                    val destination = CategoryAnalysisKey(
                        categoryId = key.categoryId,
                        type = key.type.name,
                        period = key.period.name,
                        anchorEpochDay = key.anchorDate.toEpochDay(),
                    )
                    if (navigation.activeStack.lastOrNull() != destination) navigation.push(destination)
                },
            )
        }
        entry<ProfileKey> {
            val stats by viewModel.stats.collectAsStateWithLifecycle()
            ProfileScreen(
                recordCount = stats.entryCount.toLong(),
                recordDays = stats.bookkeepingDays.toLong(),
                onManageCategories = { categoryManagerType = UiEntryType.EXPENSE },
                onImportUri = viewModel::previewImport,
                onExportUri = viewModel::export,
                currentMonth = YearMonth.from(viewModel.currentDate),
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
        entry<DiscoverKey> {
            DiscoverScreen()
        }
        entry<EntryEditorKey> { key ->
            val editorEntry by viewModel.editorEntry.collectAsStateWithLifecycle()
            val editorLoading by viewModel.editorLoading.collectAsStateWithLifecycle()
            val mode = runCatching { EntryEditorMode.valueOf(key.mode) }.getOrDefault(EntryEditorMode.FULL)
            LaunchedEffect(key.entryId, key.mode) { viewModel.prepareEditor(key.entryId) }
            val launchSession = entryLaunchSession
            val editorContentReady = !editorLoading &&
                (key.entryId != null || newEntrySeedReady) &&
                (key.entryId == null || editorEntry?.id == key.entryId)
            if (!editorContentReady) {
                BackHandler { dismissEditor(key) }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    editorLoading || (key.entryId == null && !newEntrySeedReady) ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    key.entryId != null && editorEntry?.id != key.entryId -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("账目不存在或已被删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> EntryEditorScreen(
                        existing = editorEntry?.takeIf { key.entryId != null && it.id == key.entryId },
                        categories = categories,
                        recentNoteState = recentNoteState,
                        today = viewModel.currentDate,
                        isSaving = isSaving,
                        mode = mode,
                        initialCategoryId = recentCategories[UiEntryType.EXPENSE],
                        preferredCategoryIds = recentCategories,
                        onRecentNoteContextChanged = viewModel::setRecentNoteContext,
                        onManageCategories = { categoryManagerType = it },
                        onDismiss = { dismissEditor(key) },
                        onSave = { result ->
                            val onSaved: (Long) -> Unit = { savedId ->
                                if (navigation.activeStack.lastOrNull() == key) {
                                    if (result.id == null) {
                                        if (pendingEntryReturnMotion == null) {
                                            motionSession += 1
                                            pendingEntrySaveMotion = PendingEntrySaveMotion(
                                                sessionId = motionSession,
                                                savedId = savedId,
                                                entryDate = result.date,
                                                editorKey = key,
                                            )
                                        }
                                    } else {
                                        navigation.pop()
                                        viewModel.clearRecentNoteContext()
                                    }
                                }
                            }
                            when (mode) {
                                EntryEditorMode.CATEGORY_ONLY -> result.id?.let { id ->
                                    viewModel.updateEntryCategory(id, result.categoryId, onSaved)
                                }
                                EntryEditorMode.NOTE_ONLY -> result.id?.let { id ->
                                    viewModel.updateEntryNote(id, result.note, onSaved)
                                }
                                EntryEditorMode.AMOUNT_DATE_ONLY -> result.id?.let { id ->
                                    viewModel.updateEntryAmountAndDate(
                                        id = id,
                                        amountCents = result.amountCents,
                                        date = result.date,
                                        onSaved = onSaved,
                                    )
                                }
                                EntryEditorMode.NEW, EntryEditorMode.FULL ->
                                    viewModel.saveEntry(result, onSaved)
                            }
                        },
                        onDelete = editorEntry?.let {
                            { entry ->
                                viewModel.deleteEntry(entry.id) {
                                    if (navigation.activeStack.lastOrNull() == key) {
                                        navigation.pop()
                                        viewModel.clearRecentNoteContext()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (key.entryId == null && launchSession != null) {
                                    Modifier.onGloballyPositioned {
                                        entryDestinationReadySession = launchSession
                                    }
                                } else {
                                    Modifier
                                }
                            ),
                    )
                }
            }
        }
        entry<CategoryAnalysisKey> { key ->
            val analysisCategory by chartViewModel.analysisCategory.collectAsStateWithLifecycle()
            val analysisType by chartViewModel.analysisType.collectAsStateWithLifecycle()
            val analysisKind by chartViewModel.analysisPeriodKind.collectAsStateWithLifecycle()
            val analysisLabel by chartViewModel.analysisPeriodLabel.collectAsStateWithLifecycle()
            val analysisAnchor by chartViewModel.analysisPeriodAnchor.collectAsStateWithLifecycle()
            val analysisToday by chartViewModel.currentDate.collectAsStateWithLifecycle()
            val analysisPoints by chartViewModel.analysisPoints.collectAsStateWithLifecycle()
            val analysisEntries by chartViewModel.analysisEntries.collectAsStateWithLifecycle()
            val analysisTotal by chartViewModel.analysisTotalForType.collectAsStateWithLifecycle()
            val analysisBreakdown by chartViewModel.analysisBreakdown.collectAsStateWithLifecycle()
            val selectedBreakdown by chartViewModel.selectedBreakdownId.collectAsStateWithLifecycle()
            val analysisOrder by chartViewModel.analysisRankingOrder.collectAsStateWithLifecycle()
            val keyType = runCatching { UiEntryType.valueOf(key.type) }.getOrDefault(UiEntryType.EXPENSE)
            val keyPeriod = runCatching { UiChartPeriod.valueOf(key.period) }.getOrDefault(UiChartPeriod.MONTH)
            LaunchedEffect(key) {
                chartViewModel.ensureCategoryAnalysis(
                    categoryId = key.categoryId,
                    type = keyType,
                    period = keyPeriod,
                    anchorDate = LocalDate.ofEpochDay(key.anchorEpochDay),
                )
            }
            CategoryAnalysisScreen(
                category = analysisCategory,
                type = analysisType,
                period = analysisKind,
                periodLabel = analysisLabel,
                periodAnchorDate = analysisAnchor,
                today = analysisToday,
                points = analysisPoints,
                entries = analysisEntries,
                totalForTypeCents = analysisTotal,
                breakdown = analysisBreakdown,
                selectedBreakdownId = selectedBreakdown,
                rankingOrder = analysisOrder,
                onBack = {
                    chartViewModel.closeCategoryAnalysis()
                    navigation.pop()
                },
                onPeriodChange = chartViewModel::setAnalysisPeriod,
                onPeriodSelected = chartViewModel::selectAnalysisAnchor,
                onBreakdownSelect = chartViewModel::selectBreakdown,
                onRankingOrderChange = chartViewModel::setAnalysisRankingOrder,
                onEntryClick = { openEditor(it.id, EntryEditorMode.FULL) },
            )
        }
    }

    val fallbackBackKey = navigation.activeStack.lastOrNull()
    if (
        !navigation.isAtRoot &&
        entryLaunchSession == null &&
        pendingEntrySaveMotion == null &&
        pendingEntryReturnMotion == null &&
        fallbackBackKey !is EntryEditorKey
    ) {
        BackHandler {
            if (fallbackBackKey is CategoryAnalysisKey) chartViewModel.closeCategoryAnalysis()
            navigation.pop()
        }
    }

    // The launch preview lives outside Scaffold, so both hosts must share the same viewport.
    // Otherwise removing the preview shifts the real editor by the status/navigation-bar insets.
    val routeContentInsets = ScaffoldDefaults.contentWindowInsets
    val entryMotionActive = entryLaunchSession != null ||
        pendingEntrySaveMotion != null ||
        pendingEntryReturnMotion != null
    val editing = entryMotionActive || navigation.activeStack.lastOrNull() is EntryEditorKey ||
        quickAmountTarget != null || quickNoteEntry != null
    val importantMessageVisible = snackbar.currentSnackbarData != null
    LaunchedEffect(saveConfirmation, importantMessageVisible) {
        if (importantMessageVisible) saveConfirmation?.let(viewModel.entrySaveFeedback::dismiss)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.then(
                if (entryMotionActive) Modifier.semantics { hideFromAccessibility() } else Modifier
            ),
            snackbarHost = {
                SnackbarHost(snackbar)
                if (!editing && !importantMessageVisible) {
                    saveConfirmation?.let { confirmation ->
                        EntrySaveSnackbar(confirmation, viewModel.entrySaveFeedback::dismiss)
                    }
                }
            },
            contentWindowInsets = routeContentInsets,
            bottomBar = {
                if (navigation.isAtRoot) {
                    val selectedTab = destinations.firstOrNull { it.key == navigation.active }?.tab
                        ?: SakuBottomTab.DETAIL
                    SakuBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = { tab ->
                            viewModel.entrySaveFeedback.beginInteraction()
                            destinations.firstOrNull { it.tab == tab }?.let { navigation.active = it.key }
                        },
                        onEntryClick = ::launchNewEntry,
                        entryEnabled = newEntrySeedReady &&
                            entryLaunchSession == null &&
                            pendingEntrySaveMotion == null &&
                            pendingEntryReturnMotion == null,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            pendingEntryReturnMotion?.let { pending ->
                                if (navigation.isAtRoot && coordinates.size.width > 0 && coordinates.size.height > 0) {
                                    entryReturnBottomBarReadySession = pending.sessionId
                                }
                            }
                        },
                    )
                }
            },
        ) { padding ->
            KeepAliveTabs(
                entries = navigation.entries(provider),
                activeRoute = navigation.activeRoute,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .then(
                        if (navigation.isAtRoot) {
                            Modifier
                                .testTag("entry_root_viewport")
                                .onGloballyPositioned {
                                    pendingEntryReturnMotion?.let { pending ->
                                        if (navigation.isAtRoot) {
                                            entryReturnRootReadySession = pending.sessionId
                                        }
                                    }
                                }
                        } else {
                            Modifier
                        }
                    ),
            )
        }

        entryLaunchSession?.let { sessionId ->
            EntryLaunchOverlay(
                sessionId = sessionId,
                destinationReady = entryDestinationReadySession == sessionId,
                onCovered = {
                    val key = EntryEditorKey(entryId = null, mode = EntryEditorMode.NEW.name)
                    if (navigation.activeStack.lastOrNull() != key) navigation.push(key)
                },
                onFinished = {
                    if (entryLaunchSession == sessionId) {
                        entryLaunchSession = null
                        entryDestinationReadySession = null
                    }
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(routeContentInsets),
                ) {
                    if (!newEntrySeedReady) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        EntryEditorScreen(
                            existing = null,
                            categories = categories,
                            recentNoteState = UiRecentNoteState(),
                            today = viewModel.currentDate,
                            isSaving = false,
                            mode = EntryEditorMode.NEW,
                            initialCategoryId = recentCategories[UiEntryType.EXPENSE],
                            preferredCategoryIds = recentCategories,
                            onRecentNoteContextChanged = { _, _ -> },
                            onManageCategories = {},
                            onDismiss = {},
                            onSave = {},
                            modifier = Modifier.fillMaxSize(),
                            motionPreview = true,
                        )
                    }
                }
            }
        }

        pendingEntryReturnMotion?.let { pending ->
            entryReturnMotionState?.let { returnState ->
                EntryReturnOverlay(
                    sessionId = pending.sessionId,
                    state = returnState,
                    readyToAnimate = imeHidden,
                    destinationReady = entryReturnRootReadySession == pending.sessionId &&
                        entryReturnBottomBarReadySession == pending.sessionId,
                    onCollapsed = {
                        if (
                            pendingEntryReturnMotion?.sessionId == pending.sessionId &&
                            navigation.activeStack.lastOrNull() == pending.editorKey
                        ) {
                            navigation.pop()
                            viewModel.clearRecentNoteContext()
                        }
                    },
                    onFinished = {
                        if (pendingEntryReturnMotion?.sessionId == pending.sessionId) {
                            pendingEntryReturnMotion = null
                            entryReturnRootReadySession = null
                            entryReturnBottomBarReadySession = null
                        }
                    },
                    onReadinessTimeout = {
                        if (pendingEntryReturnMotion?.sessionId == pending.sessionId) {
                            pendingEntryReturnMotion = null
                            entryReturnRootReadySession = null
                            entryReturnBottomBarReadySession = null
                            appScope.launch {
                                snackbar.showSnackbar("键盘尚未完全收起，请重试返回")
                            }
                        }
                    },
                )
            }
        }

        pendingEntrySaveMotion?.let { pending ->
            EntrySaveSuccessOverlay(
                sessionId = pending.sessionId,
                onFinished = {
                    if (pendingEntrySaveMotion?.sessionId == pending.sessionId) {
                        if (navigation.activeStack.lastOrNull() == pending.editorKey) navigation.pop()
                        viewModel.clearRecentNoteContext()
                        viewModel.revealSavedEntry(pending.entryDate)
                        recentlyCreatedEntryId = pending.savedId
                        navigation.active = DetailKey
                        pendingEntrySaveMotion = null
                    }
                },
            )
        }
    }

    if (entryMotionActive) BackHandler { }

    quickAmountTarget?.let { (entry, bounds) ->
        QuickAmountEditorOverlay(
            entry = entry,
            sourceBoundsInWindow = bounds,
            today = viewModel.currentDate,
            isSaving = isSaving,
            onDismiss = { quickAmountTarget = null },
            onSave = { amountCents, date ->
                viewModel.updateEntryAmountAndDate(entry.id, amountCents, date) { _ ->
                    if (quickAmountTarget?.first?.id == entry.id) quickAmountTarget = null
                }
            },
        )
    }

    quickNoteEntry?.let { entry ->
        QuickNoteEditorDialog(
            entry = entry,
            isSaving = isSaving,
            onDismiss = { quickNoteEntry = null },
            onSave = { note ->
                viewModel.updateEntryNote(entry.id, note) { _ ->
                    if (quickNoteEntry?.id == entry.id) quickNoteEntry = null
                }
            },
        )
    }

    detailMonthPickerInitial?.let { initialMonth ->
        SakuMonthPickerDialog(
            initialMonth = initialMonth,
            today = viewModel.currentDate,
            onDismiss = { detailMonthPickerInitial = null },
            onConfirm = { month ->
                viewModel.setMonth(month)
                detailMonthPickerInitial = null
            },
        )
    }

    categoryManagerType?.let { initialType ->
        CategoryManagerSheet(
            initialType = initialType,
            categories = categories,
            onDismiss = { categoryManagerType = null },
            onCreate = viewModel::createCategory,
            onUpdate = viewModel::updateCategory,
            onReorder = viewModel::reorderCategories,
            onArchive = viewModel::archiveCategory,
        )
    }

    importBatch?.let { batch ->
        val previewRows = remember(batch) { viewModel.importRowsToUi(batch) }
        ImportPreviewSheet(
            fileName = batch.fileName,
            rows = previewRows,
            isCommitting = isImporting,
            onDismiss = viewModel::dismissImport,
            onCommit = viewModel::commitImport,
        )
    }
}
