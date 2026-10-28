package caib.charge.ui.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import caib.charge.ui.components.CaibTextButton
import caib.charge.domain.model.SubscriptionProject
import caib.charge.ui.components.CaibSnackbarHost
import kotlinx.coroutines.flow.collectLatest

/** One app navigation entry; editor drafts and return target survive activity recreation. */
@Composable
fun SubscriptionRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val projects = overview.projects
    val today = overview.today
    val editorToday by viewModel.today.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val readError by viewModel.readError.collectAsStateWithLifecycle()
    val loaded = overview.loaded
    var screen by rememberSaveable { mutableStateOf("list") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val minimumRevisions = rememberSaveable(saver = listSaver<SnapshotStateMap<Long, Long>, Long>(
        save = { revisions -> revisions.entries.flatMap { listOf(it.key, it.value) } },
        restore = { values -> values.chunked(2).map { it[0] to it[1] }.toMutableStateMap() },
    )) { mutableStateMapOf<Long, Long>() }
    var periodId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorModeName by rememberSaveable { mutableStateOf(SubscriptionEditorMode.NEW.name) }
    var editorRevision by rememberSaveable { mutableLongStateOf(0L) }
    var editorSession by rememberSaveable { mutableIntStateOf(0) }
    var editorReturn by rememberSaveable { mutableStateOf("list") }
    var filterName by rememberSaveable { mutableStateOf(SubscriptionFilter.ALL.name) }
    val selected = projects.firstOrNull { it.id == selectedId }
    val detailReady = selected != null && selected.revision >= (minimumRevisions[selected.id] ?: 0L)
    val latestProjects by rememberUpdatedState(projects)
    val snackbar = remember { SnackbarHostState() }
    val screenStates = rememberSaveableStateHolder()
    val viewportReady = rememberSubscriptionViewportReady()
    // Keep the initial project revision and boundaries for optimistic edit conflicts.
    var editorSnapshot by remember { mutableStateOf<SubscriptionProject?>(null) }
    val editorProject = editorSnapshot?.takeIf { it.id == selectedId }
        ?: selected?.copy(revision = editorRevision)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshToday() }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SubscriptionEvent.Saved -> {
                    selectedId = event.projectId
                    if (latestProjects.any { it.id == event.projectId && it.revision >= event.expectedRevision }) {
                        minimumRevisions.remove(event.projectId)
                    } else minimumRevisions[event.projectId] = maxOf(minimumRevisions[event.projectId] ?: 0L, event.expectedRevision)
                    editorSnapshot = null
                    screen = "detail"
                }
                is SubscriptionEvent.Deleted -> {
                    if (selectedId == event.projectId) { selectedId = null; screen = "list" }
                    screenStates.removeState("detail:${event.projectId}")
                    minimumRevisions.remove(event.projectId)
                }
            }
        }
    }
    LaunchedEffect(projects) {
        minimumRevisions.toMap().forEach { (id, revision) ->
            if (projects.any { it.id == id && it.revision >= revision }) minimumRevisions.remove(id)
        }
    }
    LaunchedEffect(error, screen) {
        if (screen != "editor" && screen != "appearance" && error != null) {
            snackbar.showSnackbar(requireNotNull(error))
            viewModel.clearError()
        }
    }

    fun openEditor(mode: SubscriptionEditorMode, targetPeriod: Long? = null) {
        if (busy) return
        viewModel.clearError()
        editorReturn = screen
        editorModeName = mode.name
        periodId = targetPeriod
        editorRevision = selected?.revision ?: 0L
        editorSnapshot = if (mode == SubscriptionEditorMode.NEW) null else selected
        editorSession += 1
        screen = "editor"
    }
    fun internalBack() {
        if (busy) return
        viewModel.clearError()
        if (screen == "detail") screen = "list" else onBack()
    }
    BackHandler(enabled = screen != "editor" && screen != "appearance") { internalBack() }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            if (readError != null) Text(requireNotNull(readError), Modifier.fillMaxWidth().padding(12.dp, 4.dp), color = subscriptionErrorContentColor(), style = MaterialTheme.typography.labelMedium)
            if (!viewportReady) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else Box(Modifier.weight(1f).fillMaxWidth()) {
                // The cache is bounded: one list plus the most recently selected project.
                // Saved state still owns scroll restoration after eviction/recreation; retaining
                // the nodes avoids reconstructing the visible text/Lazy items on every return.
                if (loaded) SubscriptionPaneHost(screen == "list", Modifier.testTag("subscription-list-pane")) {
                    screenStates.SaveableStateProvider("list") {
                        SubscriptionListScreen(
                            projects = projects, today = today, filter = SubscriptionFilter.valueOf(filterName),
                            onFilterChange = { if (screen == "list") filterName = it.name },
                            onOpen = { if (screen == "list" && !busy) { selectedId = it; screen = "detail" } },
                            onNew = { if (screen == "list") openEditor(SubscriptionEditorMode.NEW) },
                            onBack = { if (screen == "list" && !busy) onBack() },
                            inspections = overview.inspections, active = screen == "list",
                        )
                    }
                }
                // A Saved event may precede Room's new overview. A missing selected ID
                // leaves this pane absent instead of activating a stale cached project.
                selected?.let { project -> key(project.id) {
                    SubscriptionPaneHost(screen == "detail" && detailReady, Modifier.testTag("subscription-detail-pane-${project.id}")) {
                        screenStates.SaveableStateProvider("detail:${project.id}") {
                            SubscriptionDetailScreen(
                                project = project, today = today,
                                onBack = { if (screen == "detail") internalBack() },
                                onRenew = { if (screen == "detail") openEditor(SubscriptionEditorMode.RENEW) },
                                onEditPeriod = { if (screen == "detail") openEditor(SubscriptionEditorMode.EDIT_PERIOD, it) },
                                onEditProject = { if (screen == "detail") openEditor(SubscriptionEditorMode.EDIT_PROJECT) },
                                onToggleStopped = { if (screen == "detail") viewModel.toggleStopped(project) },
                                onDelete = { if (screen == "detail") viewModel.delete(project) }, busy = busy,
                                inspection = overview.inspections.getValue(project.id), active = screen == "detail" && detailReady,
                                onAppearance = {
                                    if (screen == "detail" && !busy) {
                                        viewModel.clearError()
                                        editorSnapshot = project
                                        editorRevision = project.revision
                                        editorSession += 1
                                        screen = "appearance"
                                    }
                                },
                            )
                        }
                    }
                } }
                when {
                    screen == "editor" && (SubscriptionEditorMode.valueOf(editorModeName) == SubscriptionEditorMode.NEW || editorProject != null) -> key(editorSession) {
                        val mode = SubscriptionEditorMode.valueOf(editorModeName)
                        val target = periodId
                        val snapshot = editorProject
                        val session = editorSession
                        SubscriptionEditorScreen(
                            mode = mode, project = snapshot, periodId = target,
                            today = editorToday, busy = busy, error = error,
                            onBack = {
                                if (screen == "editor" && session == editorSession && !busy) {
                                    viewModel.clearError(); screen = editorReturn; editorSnapshot = null
                                }
                            },
                            onSave = { input -> if (screen == "editor" && session == editorSession) viewModel.save(mode, snapshot, target, input) },
                            onClearError = viewModel::clearError,
                            onDeletePeriod = if (mode == SubscriptionEditorMode.EDIT_PERIOD && snapshot != null && target != null) ({
                                if (screen == "editor" && session == editorSession) {
                                    if (snapshot.periods.size == 1) viewModel.delete(snapshot)
                                    else viewModel.deletePeriod(snapshot, target)
                                }
                            }) else null,
                        )
                    }
                    screen == "appearance" && editorProject != null -> key("appearance:$editorSession") {
                        val snapshot = editorProject
                        val session = editorSession
                        SubscriptionAppearanceEditor(
                            project = snapshot, busy = busy, error = error,
                            onBack = {
                                if (screen == "appearance" && session == editorSession && !busy) {
                                    viewModel.clearError(); screen = "detail"; editorSnapshot = null
                                }
                            },
                            onSave = {
                                if (screen == "appearance" && session == editorSession) viewModel.updateAppearance(snapshot, it)
                            },
                        ) { appearance ->
                            SubscriptionListCard(
                                snapshot.copy(appearance = appearance),
                                overview.inspections[snapshot.id] ?: caib.charge.domain.SubscriptionRules.inspect(snapshot, editorToday),
                                onClick = null,
                            )
                        }
                    }
                    screen == "list" && loaded -> Unit
                    screen == "detail" && detailReady -> Unit
                    else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(if (loaded && screen != "list") "正在载入订阅…" else "正在读取订阅…")
                        if (loaded) CaibTextButton(onClick = { screen = "list" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("返回列表") }
                    }
                }
            }
        }
        CaibSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

/**
 * On recreation the Android view can initially measure edge-to-edge before Compose receives
 * system bars. Restoring a list near its end into that larger viewport clamps its saved
 * anchor irreversibly. Wait for the same raw insets as the outer Scaffold, then its layout.
 * This only delays initial composition; it adds no padding and does not run while scrolling.
 */
@Composable
private fun rememberSubscriptionViewportReady(): Boolean {
    val view = LocalView.current
    val density = LocalDensity.current
    val systemBars = WindowInsets.systemBars
    val composeBars by rememberUpdatedState(listOf(
        systemBars.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr),
        systemBars.getTop(density),
        systemBars.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr),
        systemBars.getBottom(density),
    ))
    var ready by remember(view) { mutableStateOf(false) }
    LaunchedEffect(view) {
        fun agreesWithPlatform(): Boolean {
            val bars = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.systemBars())
                ?: return false
            return view.isLaidOut && view.width > 0 && view.height > 0 &&
                composeBars == listOf(bars.left, bars.top, bars.right, bars.bottom)
        }
        repeat(12) {
            withFrameNanos { }
            if (agreesWithPlatform()) {
                withFrameNanos { }
                ready = true
                return@LaunchedEffect
            }
        }
        // Bounded fallback for hosts that deliver their first insets only when requested.
        ViewCompat.requestApplyInsets(view)
        repeat(2) { withFrameNanos { } }
        ready = true
    }
    return ready
}
