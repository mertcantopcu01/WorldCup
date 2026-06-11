@file:OptIn(kotlin.time.ExperimentalTime::class)

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt


// ── UI State ──────────────────────────────────────────────────────────────────

sealed class FixtureUiState {
    data object Loading : FixtureUiState()
    data class Success(val grouped: Map<String, List<MatchEvent>>) : FixtureUiState()
    data class Error(val message: String) : FixtureUiState()
}

enum class StageGroupFilter {
    ALL, GROUPS
}

enum class RoundFilter {
    ALL, ROUND_1, ROUND_2, ROUND_3, KNOCKOUT
}

@Composable
fun FixtureScreen(
    username: String, 
    onLogout: () -> Unit,
    onUsernameChange: (String) -> Unit
) {
    var allEvents       by remember { mutableStateOf<List<MatchEvent>>(emptyList()) }
    var predictions     by remember { mutableStateOf<Map<String, MatchPrediction>>(emptyMap()) }
    var isLoading       by remember { mutableStateOf(true) }
    var errorMsg        by remember { mutableStateOf<String?>(null) }
    var searchQuery     by remember { mutableStateOf("") }
    var activeFilter    by remember { mutableStateOf(FilterType.ALL) }
    var activeStageGroup by remember { mutableStateOf(StageGroupFilter.ALL) }
    var activeRound      by remember { mutableStateOf(RoundFilter.ALL) }
    var loadKey         by remember { mutableStateOf(0) } // for retry
    var predictingMatch by remember { mutableStateOf<MatchEvent?>(null) }
    var activeTab       by remember { mutableStateOf(MainTab.FIXTURE) }
    var isDraggingTeam  by remember { mutableStateOf(false) }
    var knockoutViewMode by remember { mutableStateOf(KnockoutViewMode.BRACKET) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showNotificationPrefs by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Fetch data
    LaunchedEffect(loadKey, username) {
        isLoading = true
        errorMsg = null
        try {
            allEvents = SportsDbApi.getFixtures()
            predictions = PredictionService.getPredictions(username)
            
            // Check if group predictions are completed
            val groupFetched = PredictionService.getGroupPredictions(username)
            val completedCount = groupFetched.keys.size
            if (completedCount >= 12) {
                activeTab = MainTab.KNOCKOUT_PREDICTION
            }
            
            // Register for FCM notifications
            if (!username.startsWith("misafir", ignoreCase = true)) {
                NotificationService.registerNotification(username)
            }
            
            isLoading = false
        } catch (e: Exception) {
            errorMsg = e.message ?: "Bağlantı hatası"
            isLoading = false
        }
    }

    val totalPoints by remember(allEvents, predictions) {
        derivedStateOf {
            allEvents.sumOf { evt ->
                if (matchStatus(evt) == MatchStatus.DONE) {
                    val pred = predictions[evt.idEvent]
                    if (pred != null) {
                        calculatePredictionPoints(pred, evt.intHomeScore, evt.intAwayScore)
                    } else 0
                } else 0
            }
        }
    }

    // Derived: filter + group
    val isGroupStageActive = activeStageGroup == StageGroupFilter.GROUPS || 
                            activeRound == RoundFilter.ROUND_1 || 
                            activeRound == RoundFilter.ROUND_2 || 
                            activeRound == RoundFilter.ROUND_3

    val groupedEvents: Map<String, List<MatchEvent>> by remember(allEvents, searchQuery, activeFilter, activeStageGroup, activeRound) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            val filtered = allEvents.filter { evt ->
                val statusOk = when (activeFilter) {
                    FilterType.ALL      -> true
                    FilterType.UPCOMING -> matchStatus(evt) != MatchStatus.DONE
                    FilterType.DONE     -> matchStatus(evt) == MatchStatus.DONE
                }
                val stageOk = when (activeStageGroup) {
                    StageGroupFilter.ALL -> {
                        when (activeRound) {
                            RoundFilter.ALL -> true
                            RoundFilter.ROUND_1 -> evt.intRound == "1"
                            RoundFilter.ROUND_2 -> evt.intRound == "2"
                            RoundFilter.ROUND_3 -> evt.intRound == "3"
                            RoundFilter.KNOCKOUT -> {
                                val r = evt.intRound ?: ""
                                r != "1" && r != "2" && r != "3"
                            }
                        }
                    }
                    StageGroupFilter.GROUPS -> {
                        when (activeRound) {
                            RoundFilter.ALL -> evt.intRound == "1" || evt.intRound == "2" || evt.intRound == "3"
                            RoundFilter.ROUND_1 -> evt.intRound == "1"
                            RoundFilter.ROUND_2 -> evt.intRound == "2"
                            RoundFilter.ROUND_3 -> evt.intRound == "3"
                            RoundFilter.KNOCKOUT -> false
                        }
                    }
                }
                val homeTr = translateTeam(evt.strHomeTeam ?: "")
                val awayTr = translateTeam(evt.strAwayTeam ?: "")
                val searchOk = q.isEmpty() ||
                    (evt.strHomeTeam ?: "").lowercase().contains(q) ||
                    (evt.strAwayTeam ?: "").lowercase().contains(q) ||
                    homeTr.lowercase().contains(q) ||
                    awayTr.lowercase().contains(q)
                statusOk && stageOk && searchOk
            }
            if (isGroupStageActive) {
                if (activeRound == RoundFilter.ALL) {
                    filtered
                        .groupBy {
                            val group = getMatchGroup(it.strHomeTeam, it.strAwayTeam)
                            val round = it.intRound ?: ""
                            "$group|$round"
                        }
                        .entries
                        .sortedWith(
                            compareBy<Map.Entry<String, List<MatchEvent>>> { it.key.substringBefore('|') }
                                .thenBy { it.key.substringAfter('|', "9").toIntOrNull() ?: 9 }
                        )
                        .associate { (key, matches) ->
                            val group = key.substringBefore('|')
                            val round = key.substringAfter('|', "")
                            val title = if (round.isNotBlank()) "$group — ${roundLabel(round)}" else group
                            title to matches.sortedBy { it.strTimestamp ?: "" }
                        }
                } else {
                    filtered
                        .groupBy { getMatchGroup(it.strHomeTeam, it.strAwayTeam) }
                        .entries
                        .sortedBy { it.key }
                        .associate { (group, matches) ->
                            group to matches.sortedBy { it.strTimestamp ?: "" }
                        }
                }
            } else {
                filtered.groupBy {
                    val trDt = convertToTurkeyDateTime(it.strTimestamp, it.dateEventLocal ?: it.dateEvent, it.strTimeLocal)
                    trDt.dateKey
                }.entries.sortedBy { it.key }.associate { it.key to it.value }
            }
        }
    }

    val listState = rememberLazyListState()

    Scaffold(containerColor = BgDeep) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                userScrollEnabled = !isDraggingTeam,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 9999.dp)
                    .padding(
                        start = if (activeTab == MainTab.KNOCKOUT_PREDICTION && knockoutViewMode == KnockoutViewMode.BRACKET) 0.dp else 12.dp,
                        end = if (activeTab == MainTab.KNOCKOUT_PREDICTION && knockoutViewMode == KnockoutViewMode.BRACKET) 0.dp else 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Hero Header
                item {
                    HeroHeader(
                        username = username,
                        totalPoints = totalPoints,
                        onLeaderboardClick = { activeTab = MainTab.LEADERBOARD },
                        onNotificationClick = { showNotificationPrefs = true },
                        onLogout = onLogout
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // Main Navigation Tabs
                item {
                    MainTabSelector(activeTab = activeTab, onSelect = { activeTab = it })
                    Spacer(Modifier.height(20.dp))
                }

                if (username.startsWith("misafir", ignoreCase = true)) {
                    item {
                        GuestUpgradeBanner(onUpgradeClick = { showUpgradeDialog = true })
                        Spacer(Modifier.height(16.dp))
                    }
                }

                when (activeTab) {
                    MainTab.FIXTURE -> {
                        // Stats
                        if (allEvents.isNotEmpty()) {
                            item {
                                StatsRow(all = allEvents)
                                Spacer(Modifier.height(18.dp))
                            }
                        }

                        // Search
                        item {
                            SearchField(
                                query = searchQuery,
                                onChange = { searchQuery = it },
                                onClear = { searchQuery = "" }
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        // Stage Filters
                        item {
                            Text(
                                "Turnuva Aşaması",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            StageFilterRow(
                                activeGroup = activeStageGroup,
                                activeRound = activeRound,
                                onSelectGroup = { group ->
                                    activeStageGroup = group
                                    if (group == StageGroupFilter.GROUPS && activeRound == RoundFilter.KNOCKOUT) {
                                        activeRound = RoundFilter.ALL
                                    }
                                },
                                onSelectRound = { round ->
                                    if (activeRound == round) {
                                        activeRound = RoundFilter.ALL
                                    } else {
                                        activeRound = round
                                        if (round == RoundFilter.KNOCKOUT && activeStageGroup == StageGroupFilter.GROUPS) {
                                            activeStageGroup = StageGroupFilter.ALL
                                        }
                                    }
                                }
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                        // Status Filters
                        item {
                            Text(
                                "Maç Durumu",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            FilterRow(active = activeFilter, onSelect = { activeFilter = it })
                            Spacer(Modifier.height(20.dp))
                        }

                        // Content
                        when {
                            isLoading -> item { LoadingState() }
                            errorMsg != null -> item { ErrorState(msg = errorMsg!!, onRetry = { loadKey++ }) }
                            groupedEvents.isEmpty() -> item { EmptyState() }
                            else -> {
                                groupedEvents.forEach { (sectionTitle, matches) ->
                                    item(key = "hdr_$sectionTitle") {
                                        if (isGroupStageActive) {
                                            SectionHeader(
                                                title = sectionTitle,
                                                matchCount = matches.size,
                                                modifier = Modifier.padding(
                                                    top = if (sectionTitle == groupedEvents.keys.first()) 0.dp else 14.dp
                                                )
                                            )
                                        } else {
                                            DateHeader(
                                                dateKey = sectionTitle,
                                                matchCount = matches.size,
                                                modifier = Modifier.padding(
                                                    top = if (sectionTitle == groupedEvents.keys.first()) 0.dp else 14.dp
                                                )
                                            )
                                        }
                                    }
                                    items(matches, key = { it.idEvent }) { evt ->
                                        MatchCard(
                                            evt = evt,
                                            prediction = predictions[evt.idEvent],
                                            onPredictClick = null,
                                            onQuickPredict = null,
                                            showDate = isGroupStageActive,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    MainTab.STANDINGS -> {
                        when {
                            isLoading -> item { LoadingState() }
                            errorMsg != null -> item { ErrorState(msg = errorMsg!!, onRetry = { loadKey++ }) }
                            else -> {
                                item {
                                    StandingsView(allEvents = allEvents)
                                }
                            }
                        }
                    }
                    MainTab.GROUP_PREDICTION -> {
                        when {
                            isLoading -> item { LoadingState() }
                            errorMsg != null -> item { ErrorState(msg = errorMsg!!, onRetry = { loadKey++ }) }
                            else -> {
                                item {
                                    GroupPredictionView(
                                        allEvents = allEvents,
                                        username = username,
                                        onDraggingChanged = { isDraggingTeam = it },
                                        onAllGroupsSaved = { activeTab = MainTab.KNOCKOUT_PREDICTION }
                                    )
                                }
                            }
                        }
                    }
                    MainTab.KNOCKOUT_PREDICTION -> {
                        when {
                            isLoading -> item { LoadingState() }
                            errorMsg != null -> item { ErrorState(msg = errorMsg!!, onRetry = { loadKey++ }) }
                            else -> {
                                item {
                                    KnockoutPredictionView(
                                        allEvents = allEvents,
                                        predictions = predictions,
                                        username = username,
                                        viewMode = knockoutViewMode,
                                        onViewModeChange = { knockoutViewMode = it }
                                    )
                                }
                            }
                        }
                    }
                    MainTab.LEADERBOARD -> {
                        when {
                            isLoading -> item { LoadingState() }
                            errorMsg != null -> item { ErrorState(msg = errorMsg!!, onRetry = { loadKey++ }) }
                            else -> {
                                item {
                                    LeaderboardView(allEvents = allEvents, currentUsername = username)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Veriler TheSportsDB tarafından sağlanmaktadır",
                        color = TextSecond.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                    )
                }
            }

            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 6.dp),
                adapter = rememberScrollbarAdapter(listState),
                style = ScrollbarStyle(
                    minimalHeight = 32.dp,
                    thickness = 8.dp,
                    shape = RoundedCornerShape(4.dp),
                    hoverDurationMillis = 250,
                    unhoverColor = Color.White.copy(alpha = 0.18f),
                    hoverColor = Gold.copy(alpha = 0.75f)
                )
            )
        }
    }

    // Prediction Dialog
    if (predictingMatch != null) {
        PredictionDialog(
            match = predictingMatch!!,
            currentPrediction = predictions[predictingMatch!!.idEvent],
            onDismiss = { predictingMatch = null },
            onSave = { home, away ->
                scope.launch {
                    try {
                        val success = PredictionService.savePrediction(username, predictingMatch!!.idEvent, home, away)
                        if (success) {
                            predictions = predictions + (predictingMatch!!.idEvent to MatchPrediction(home, away))
                        }
                    } catch (e: Exception) {
                        // Ignore
                    } finally {
                        predictingMatch = null
                    }
                }
            }
        )
    }

    // Guest Upgrade Dialog
    if (showUpgradeDialog) {
        GuestUpgradeDialog(
            guestUsername = username,
            onDismiss = { showUpgradeDialog = false },
            onUpgradeSuccess = { newUsername ->
                showUpgradeDialog = false
                onUsernameChange(newUsername)
            }
        )
    }

    // Notification Preferences Dialog
    if (showNotificationPrefs) {
        NotificationPreferencesDialog(
            username = username,
            allEvents = allEvents,
            onDismiss = { showNotificationPrefs = false }
        )
    }
}

// ── Hero ──────────────────────────────────────────────────────────────────────

@Composable
private fun HeroHeader(
    username: String,
    totalPoints: Int,
    onLeaderboardClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onLogout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0A1220), BgDeep))
            )
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.SportsSoccer,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "FIFA World Cup 2026",
                color = Gold,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            Text(
                "Kanada · ABD · Meksika",
                color = TextSecond,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(12.dp))
            WorldCupCountdown(modifier = Modifier.padding(horizontal = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    "Kullanıcı: $username",
                    color = TextSecond,
                    fontSize = 12.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(GoldGlow)
                        .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable { onLeaderboardClick() }
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "$totalPoints Puan",
                            color = Gold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Sıralama",
                            tint = Gold.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, end = 8.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!username.startsWith("misafir", ignoreCase = true)) {
                    IconButton(onClick = onNotificationClick) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Bildirim Tercihleri",
                            tint = TextSecond
                        )
                    }
                }
                IconButton(onClick = onLogout) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Çıkış Yap",
                        tint = TextSecond
                    )
                }
            }
        }
    }
}

// ── Stats ─────────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(all: List<MatchEvent>) {
    val done     = all.count { matchStatus(it) == MatchStatus.DONE }
    val upcoming = all.count { matchStatus(it) != MatchStatus.DONE }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatChip("Toplam",     all.size.toString(), Modifier.weight(1f))
        StatChip("Tamamlanan", done.toString(),      Modifier.weight(1f))
        StatChip("Bekleyen",   upcoming.toString(),  Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Gold, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = TextSecond, fontSize = 10.sp)
        }
    }
}

// ── Search ────────────────────────────────────────────────────────────────────

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Takım ara…", color = TextSecond, fontSize = 14.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecond,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Temizle",
                        tint = TextSecond,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(40.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor    = GoldDim,
            unfocusedBorderColor  = BorderColor,
            focusedTextColor      = TextPrimary,
            unfocusedTextColor    = TextPrimary,
            cursorColor           = Gold,
            focusedContainerColor   = BgCard,
            unfocusedContainerColor = BgCard
        )
    )
}

// ── Filters ───────────────────────────────────────────────────────────────────

@Composable
private fun StageFilterRow(
    activeGroup: StageGroupFilter,
    activeRound: RoundFilter,
    onSelectGroup: (StageGroupFilter) -> Unit,
    onSelectRound: (RoundFilter) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Upper Row: Tümü & Grup Maçları
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val upperStages = listOf(StageGroupFilter.ALL, StageGroupFilter.GROUPS)
            upperStages.forEach { f ->
                val label = when (f) {
                    StageGroupFilter.ALL    -> "Tümü"
                    StageGroupFilter.GROUPS -> "Grup Maçları"
                }
                val isActive = f == activeGroup
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(40.dp))
                        .background(if (isActive) Gold else BgCard)
                        .clickable { onSelectGroup(f) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isActive) BgDeep else TextSecond,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
        
        // Lower Row: 1. Tur, 2. Tur, 3. Tur, Eleme Turları
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val lowerStages = if (activeGroup == StageGroupFilter.GROUPS) {
                listOf(RoundFilter.ROUND_1, RoundFilter.ROUND_2, RoundFilter.ROUND_3)
            } else {
                listOf(RoundFilter.ROUND_1, RoundFilter.ROUND_2, RoundFilter.ROUND_3, RoundFilter.KNOCKOUT)
            }
            lowerStages.forEach { f ->
                val label = when (f) {
                    RoundFilter.ROUND_1  -> "1. Tur"
                    RoundFilter.ROUND_2  -> "2. Tur"
                    RoundFilter.ROUND_3  -> "3. Tur"
                    RoundFilter.KNOCKOUT -> "Eleme Turları"
                    else                 -> ""
                }
                val isActive = f == activeRound
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(40.dp))
                        .background(if (isActive) Gold else BgCard)
                        .clickable { onSelectRound(f) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isActive) BgDeep else TextSecond,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(active: FilterType, onSelect: (FilterType) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterType.entries.forEach { f ->
            val label = when (f) {
                FilterType.ALL -> "Tümü"; FilterType.UPCOMING -> "Bekleyen"; FilterType.DONE -> "Tamamlanan"
            }
            val isActive = f == active
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(40.dp))
                    .background(if (isActive) Gold else BgCard)
                    .clickable { onSelect(f) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isActive) BgDeep else TextSecond,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ── States ────────────────────────────────────────────────────────────────────

@Composable private fun LoadingState() {
    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = Gold, strokeWidth = 3.dp)
            Text("Fikstür yükleniyor…", color = TextSecond, fontSize = 14.sp)
        }
    }
}

@Composable private fun ErrorState(msg: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = RedError,
                modifier = Modifier.size(44.dp)
            )
            Text("Veri yüklenemedi", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(msg, color = TextSecond, fontSize = 13.sp, textAlign = TextAlign.Center)
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = BgDeep,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Tekrar Dene", color = BgDeep, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable private fun EmptyState() {
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecond,
                modifier = Modifier.size(44.dp)
            )
            Text("Aramanıza uygun maç bulunamadı", color = TextSecond, fontSize = 14.sp)
        }
    }
}

@Composable
fun PredictionDialog(
    match: MatchEvent,
    currentPrediction: MatchPrediction?,
    onDismiss: () -> Unit,
    onSave: (homeScore: Int, awayScore: Int) -> Unit
) {
    var homeScore by remember { mutableStateOf(currentPrediction?.homeScore ?: 0) }
    var awayScore by remember { mutableStateOf(currentPrediction?.awayScore ?: 0) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Skor Tahmini Yap",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = roundLabel(match.intRound),
                    color = GoldDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.width(100.dp)
                    ) {
                        TeamBadge(
                            name = translateTeam(match.strHomeTeam ?: ""),
                            badgeUrl = getTeamFlagUrl(match.strHomeTeam ?: "")
                        )
                        Text(
                            text = translateTeam(match.strHomeTeam ?: ""),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { if (homeScore > 0) homeScore-- },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(BgCard, CircleShape)
                                    .border(1.dp, BorderColor, CircleShape)
                            ) {
                                Text("-", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = homeScore.toString(),
                                color = Gold,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            IconButton(
                                onClick = { homeScore++ },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(BgCard, CircleShape)
                                    .border(1.dp, BorderColor, CircleShape)
                            ) {
                                Text("+", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = "—",
                        color = TextSecond,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.width(100.dp)
                    ) {
                        TeamBadge(
                            name = translateTeam(match.strAwayTeam ?: ""),
                            badgeUrl = getTeamFlagUrl(match.strAwayTeam ?: "")
                        )
                        Text(
                            text = translateTeam(match.strAwayTeam ?: ""),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { if (awayScore > 0) awayScore-- },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(BgCard, CircleShape)
                                    .border(1.dp, BorderColor, CircleShape)
                            ) {
                                Text("-", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = awayScore.toString(),
                                color = Gold,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            IconButton(
                                onClick = { awayScore++ },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(BgCard, CircleShape)
                                    .border(1.dp, BorderColor, CircleShape)
                            ) {
                                Text("+", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    onSave(homeScore, awayScore)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(20.dp),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BgDeep, strokeWidth = 2.dp)
                } else {
                    Text("Kaydet", color = BgDeep, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("İptal", color = TextSecond)
            }
        },
        containerColor = BgCard,
        shape = RoundedCornerShape(18.dp)
    )
}

enum class MainTab { FIXTURE, STANDINGS, GROUP_PREDICTION, KNOCKOUT_PREDICTION, LEADERBOARD }

@Composable
private fun MainTabSelector(activeTab: MainTab, onSelect: (MainTab) -> Unit) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val label = when (tab) {
                    MainTab.FIXTURE             -> "Fikstür"
                    MainTab.STANDINGS           -> "Puan Durumu"
                    MainTab.GROUP_PREDICTION    -> "Grup Tahmini"
                    MainTab.KNOCKOUT_PREDICTION -> "Eleme Tahmini"
                    MainTab.LEADERBOARD         -> "Liderlik"
                }
                val isActive = tab == activeTab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isActive) Gold else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isActive) BgDeep else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun StandingsView(allEvents: List<MatchEvent>) {
    val standings by remember(allEvents) {
        derivedStateOf { calculateStandings(allEvents) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        standings.forEach { (groupName, list) ->
            GroupStandingsTable(groupName = groupName, standings = list)
        }
    }
}

@Composable
fun GroupStandingsTable(groupName: String, standings: List<TeamStanding>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = groupName,
                color = Gold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                Text("Takım", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(3f))
                Text("O", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                Text("G", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                Text("B", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                Text("M", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                Text("AG", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                Text("YG", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                Text("AV", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("P", color = GoldDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.Center)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderColor)
            )

            standings.forEachIndexed { index, row ->
                val rank = index + 1
                val isQualifying = rank <= 2
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(0.7f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isQualifying) GreenLive.copy(alpha = 0.15f) else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = rank.toString(),
                                color = if (isQualifying) GreenLive else TextSecond,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.weight(3f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TeamBadge(
                            name = translateTeam(row.teamName),
                            badgeUrl = getTeamFlagUrl(row.teamName)
                        )
                        Text(
                            text = translateTeam(row.teamName),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(text = row.played.toString(), color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                    Text(text = row.won.toString(), color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                    Text(text = row.drawn.toString(), color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                    Text(text = row.lost.toString(), color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                    Text(text = row.goalsFor.toString(), color = TextSecond, fontSize = 12.sp, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                    Text(text = row.goalsAgainst.toString(), color = TextSecond, fontSize = 12.sp, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                    
                    val avSign = if (row.goalDifference > 0) "+${row.goalDifference}" else row.goalDifference.toString()
                    Text(
                        text = avSign,
                        color = if (row.goalDifference > 0) GreenLive else if (row.goalDifference < 0) RedError else TextSecond,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = row.points.toString(),
                        color = Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1.1f),
                        textAlign = TextAlign.Center
                    )
                }

                if (index < standings.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(BorderColor.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

sealed class LeaderboardUiState {
    data object Loading : LeaderboardUiState()
    data class Success(val entries: List<UserLeaderboardEntry>) : LeaderboardUiState()
    data class Error(val message: String) : LeaderboardUiState()
}

@Composable
fun LeaderboardView(allEvents: List<MatchEvent>, currentUsername: String) {
    var uiState by remember { mutableStateOf<LeaderboardUiState>(LeaderboardUiState.Loading) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey, allEvents) {
        if (allEvents.isEmpty()) return@LaunchedEffect
        uiState = LeaderboardUiState.Loading
        try {
            val entries = PredictionService.getLeaderboard(allEvents)
            uiState = LeaderboardUiState.Success(entries)
        } catch (e: Exception) {
            uiState = LeaderboardUiState.Error(e.message ?: "Veriler yüklenirken bir hata oluştu.")
        }
    }

    when (val state = uiState) {
        is LeaderboardUiState.Loading -> {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold, strokeWidth = 3.dp)
            }
        }
        is LeaderboardUiState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(state.message, color = RedError, fontSize = 14.sp, textAlign = TextAlign.Center)
                Button(
                    onClick = { refreshKey++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = BgDeep, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Yeniden Dene", color = BgDeep, fontWeight = FontWeight.Bold)
                }
            }
        }
        is LeaderboardUiState.Success -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Kullanıcı Sıralaması",
                        color = Gold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                        Text("Kullanıcı", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.5f))
                        Text("Tahmin", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("Tam Skor", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
                        Text("Grup", color = TextSecond, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                        Text("Puan", color = GoldDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderColor)
                    )

                    state.entries.forEachIndexed { index, entry ->
                        val rank = index + 1
                        val isCurrentUser = entry.username.equals(currentUsername, ignoreCase = true)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrentUser) GoldGlow else Color.Transparent)
                                .border(1.dp, if (isCurrentUser) Gold.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(8.dp))
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(0.7f),
                                contentAlignment = Alignment.Center
                            ) {
                                when (rank) {
                                    1 -> Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = "1.",
                                        tint = Gold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    2 -> Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = "2.",
                                        tint = Color(0xFFC0C0C0), // Silver
                                        modifier = Modifier.size(16.dp)
                                    )
                                    3 -> Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = "3.",
                                        tint = Color(0xFFCD7F32), // Bronze
                                        modifier = Modifier.size(16.dp)
                                    )
                                    else -> Text(
                                        text = rank.toString(),
                                        color = TextSecond,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = if (isCurrentUser) "${entry.username} (Siz)" else entry.username,
                                color = if (isCurrentUser) Gold else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.SemiBold,
                                modifier = Modifier.weight(2.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = entry.predictionCount.toString(),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = entry.exactMatchesCount.toString(),
                                color = GreenLive,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1.2f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = if (entry.groupPredictionPoints > 0) "+${entry.groupPredictionPoints}" else entry.groupPredictionPoints.toString(),
                                color = if (entry.groupPredictionPoints > 0) Gold.copy(alpha = 0.75f) else TextSecond,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(0.9f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = entry.totalPoints.toString(),
                                color = Gold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }

                        if (index < state.entries.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(BorderColor.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Group Prediction View ─────────────────────────────────────────────────────

@Composable
fun GroupPredictionView(
    allEvents: List<MatchEvent>,
    username: String,
    onDraggingChanged: (Boolean) -> Unit,
    onAllGroupsSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Encoded group label -> mutable list of canonical team names
    var groupPredictions by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var savedGroups     by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savingGroups    by remember { mutableStateOf<Set<String>>(emptySet()) }
    var errorGroups     by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading       by remember { mutableStateOf(true) }
    var activeGroupIndex by remember { mutableStateOf(0) }

    // Load existing predictions from Firebase
    LaunchedEffect(username) {
        isLoading = true
        val fetched = try {
            PredictionService.getGroupPredictions(username)
        } catch (_: Exception) { emptyMap() }

        // Initialize every group: use saved prediction or default (group order)
        val initial = mutableMapOf<String, List<String>>()
        worldCupGroups.forEach { group ->
            val encoded = group.label.replace(" ", "_").lowercase()
            initial[encoded] = fetched[encoded]?.rankings?.takeIf { it.size == group.teams.size }
                ?: group.teams.toList()
        }
        groupPredictions = initial
        savedGroups = fetched.keys.toSet()
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold, strokeWidth = 3.dp)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Info card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = GoldGlow)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(22.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Grup Sıralaması Tahmini",
                        color = Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Her gruptaki takımların 1.–4. bitireceği sırayı tahmin et. " +
                        "Her doğru tahmin = 10p · Grupta 4'ü de doğru olunca = 50p. " +
                        "Sıralamayı değiştirmek için takımın yanındaki taşıma simgesini basılı tutup yukarı/aşağı sürükleyin. " +
                        "Grubun ilk maçından 1 saat önce kilitlenir.",
                        color = TextSecond,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Top Group Selector tabs
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            worldCupGroups.forEachIndexed { index, g ->
                val encoded = g.label.replace(" ", "_").lowercase()
                val isSaved = encoded in savedGroups
                val isActive = index == activeGroupIndex
                
                val tabBorderColor = when {
                    isActive -> Gold
                    isSaved -> GreenLive.copy(alpha = 0.6f)
                    else -> BorderColor
                }
                
                val tabBgColor = when {
                    isActive -> Gold
                    else -> BgCard
                }
                
                val tabTextColor = when {
                    isActive -> BgDeep
                    isSaved -> GreenLive
                    else -> TextSecond
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(tabBgColor)
                        .border(1.dp, tabBorderColor, RoundedCornerShape(20.dp))
                        .clickable { activeGroupIndex = index }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = g.label.substringBefore(" "), // extracts "A" from "A Grubu"
                            color = tabTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSaved) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Kaydedildi",
                                tint = tabTextColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        val savedCount = worldCupGroups.count { 
            it.label.replace(" ", "_").lowercase() in savedGroups 
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${worldCupGroups[activeGroupIndex].label} Tahmini",
                color = Gold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tamamlanan: $savedCount / 12",
                color = TextSecond,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Active Group Card
        val group = worldCupGroups[activeGroupIndex]
        val encoded = group.label.replace(" ", "_").lowercase()
        val currentRankings = groupPredictions[encoded] ?: group.teams.toList()
        val isLocked = isGroupPredictionLocked(group.label, allEvents)
        val isSaved  = encoded in savedGroups
        val isSaving = encoded in savingGroups
        val errorMsg = errorGroups[encoded]
        val hasNextGroup = activeGroupIndex < worldCupGroups.lastIndex

        GroupPredictionCard(
            group     = group,
            rankings  = currentRankings,
            isLocked  = isLocked,
            isSaved   = isSaved,
            isSaving  = isSaving,
            errorMsg  = errorMsg,
            hasNextGroup = hasNextGroup,
            onDraggingChanged = onDraggingChanged,
            onMove    = { fromIdx, toIdx ->
                if (!isLocked) {
                    val newList = currentRankings.toMutableList()
                    val item = newList.removeAt(fromIdx)
                    newList.add(toIdx, item)
                    groupPredictions = groupPredictions + (encoded to newList)
                }
            },
            onSave = {
                scope.launch {
                    savingGroups = savingGroups + encoded
                    errorGroups = errorGroups - encoded
                    try {
                        PredictionService.saveGroupPrediction(username, group.label, currentRankings)
                        savedGroups = savedGroups + encoded
                        
                        // Automatically advance to the next group after saving successfully!
                        if (hasNextGroup) {
                            activeGroupIndex++
                        } else {
                            onAllGroupsSaved()
                        }
                    } catch (e: Exception) {
                        errorGroups = errorGroups + (encoded to (e.message ?: "Kayıt hatası"))
                    } finally {
                        savingGroups = savingGroups - encoded
                    }
                }
            }
        )

        // Previous & Next Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (activeGroupIndex > 0) activeGroupIndex-- },
                enabled = activeGroupIndex > 0,
                colors = ButtonDefaults.buttonColors(containerColor = BgCard),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Geri",
                    tint = if (activeGroupIndex > 0) Gold else TextSecond.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Önceki Grup",
                    color = if (activeGroupIndex > 0) TextPrimary else TextSecond.copy(alpha = 0.3f),
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = { if (activeGroupIndex < worldCupGroups.lastIndex) activeGroupIndex++ },
                enabled = activeGroupIndex < worldCupGroups.lastIndex,
                colors = ButtonDefaults.buttonColors(containerColor = BgCard),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                Text(
                    text = "Sonraki Grup",
                    color = if (activeGroupIndex < worldCupGroups.lastIndex) TextPrimary else TextSecond.copy(alpha = 0.3f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "İleri",
                    tint = if (activeGroupIndex < worldCupGroups.lastIndex) Gold else TextSecond.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupPredictionCard(
    group: WorldCupGroup,
    rankings: List<String>,
    isLocked: Boolean,
    isSaved: Boolean,
    isSaving: Boolean,
    errorMsg: String?,
    hasNextGroup: Boolean,
    onDraggingChanged: (Boolean) -> Unit,
    onMove: (fromIdx: Int, toIdx: Int) -> Unit,
    onSave: () -> Unit
) {
    val rankColors = listOf(
        Color(0xFFFFD700),  // 1st – Gold
        Color(0xFFC0C0C0),  // 2nd – Silver
        Color(0xFFCD7F32),  // 3rd – Bronze
        TextSecond           // 4th – Neutral
    )
    val rankLabels = listOf("1.", "2.", "3.", "4.")

    // Stable references to prevent pointerInput from capturing stale parameters
    val currentOnMove by rememberUpdatedState(onMove)
    val currentRankings by rememberUpdatedState(rankings)
    val currentOnDraggingChanged by rememberUpdatedState(onDraggingChanged)

    // Drag-to-reorder local state
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var itemHeightPx by remember { mutableStateOf(0f) }

    LaunchedEffect(group) {
        draggedIndex = null
        dragOffset = 0f
        itemHeightPx = 0f
    }

    val targetIndex = if (draggedIndex != null && itemHeightPx > 0f) {
        (draggedIndex!! + (dragOffset / itemHeightPx).roundToInt()).coerceIn(0, currentRankings.lastIndex)
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Card header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.label,
                    color = Gold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                when {
                    isLocked -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Kilitli", tint = TextSecond, modifier = Modifier.size(14.dp))
                        Text("Kilitli", color = TextSecond, fontSize = 11.sp)
                    }
                    isSaved  -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Kaydedildi", tint = GreenLive, modifier = Modifier.size(14.dp))
                        Text("Kaydedildi", color = GreenLive, fontSize = 11.sp)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderColor))

            // Column headers
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sıra", color = TextSecond, fontSize = 10.sp, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center)
                Text("Takım", color = TextSecond, fontSize = 10.sp, modifier = Modifier.weight(3f))
                if (!isLocked) {
                    Text("Taşı", color = TextSecond, fontSize = 10.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
                }
            }

            // Team rows
            rankings.forEachIndexed { index, teamName ->
                // Calculate animation shift for non-dragged items
                val animatedShift by animateFloatAsState(
                    targetValue = if (draggedIndex != null && targetIndex != null) {
                        val dragged = draggedIndex!!
                        val target = targetIndex!!
                        if (dragged < target && index in (dragged + 1)..target) {
                            -itemHeightPx
                        } else if (dragged > target && index in target until dragged) {
                            itemHeightPx
                        } else {
                            0f
                        }
                    } else {
                        0f
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )

                val finalOffset = if (index == draggedIndex) dragOffset else animatedShift

                val isCurrentDragged = index == draggedIndex
                val rowBackground = when {
                    isCurrentDragged -> Gold.copy(alpha = 0.12f)
                    index == 0 -> Gold.copy(alpha = 0.07f)
                    index == 1 -> Color(0xFFC0C0C0).copy(alpha = 0.05f)
                    else -> Color.Transparent
                }

                val rowBorderModifier = if (isCurrentDragged) {
                    Modifier.border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                } else Modifier

                // Drag gesture detection applied to the entire row with instant drag and scroll disable
                val dragModifier = if (!isLocked) {
                    Modifier.pointerInput(index) {
                        detectDragGestures(
                            onDragStart = {
                                draggedIndex = index
                                dragOffset = 0f
                                currentOnDraggingChanged(true)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                            },
                            onDragEnd = {
                                if (draggedIndex != null && itemHeightPx > 0) {
                                    val target = (draggedIndex!! + (dragOffset / itemHeightPx).roundToInt()).coerceIn(0, currentRankings.lastIndex)
                                    if (target != draggedIndex) {
                                        currentOnMove(draggedIndex!!, target)
                                    }
                                }
                                draggedIndex = null
                                dragOffset = 0f
                                currentOnDraggingChanged(false)
                            },
                            onDragCancel = {
                                if (draggedIndex != null && itemHeightPx > 0) {
                                    val target = (draggedIndex!! + (dragOffset / itemHeightPx).roundToInt()).coerceIn(0, currentRankings.lastIndex)
                                    if (target != draggedIndex) {
                                        currentOnMove(draggedIndex!!, target)
                                    }
                                }
                                draggedIndex = null
                                dragOffset = 0f
                                currentOnDraggingChanged(false)
                            }
                        )
                    }
                } else Modifier


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, finalOffset.roundToInt()) }
                        .zIndex(if (isCurrentDragged) 1f else 0f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(rowBackground)
                        .then(rowBorderModifier)
                        .then(dragModifier)
                        .onGloballyPositioned { coordinates ->
                            if (itemHeightPx == 0f) {
                                itemHeightPx = coordinates.size.height.toFloat()
                            }
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rank badge
                    Box(modifier = Modifier.weight(0.6f), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(rankColors[index].copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = rankLabels[index],
                                color = rankColors[index],
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Flag + Name
                    Row(
                        modifier = Modifier.weight(3f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TeamBadge(name = translateTeam(teamName), badgeUrl = getTeamFlagUrl(teamName))
                        Text(
                            text = translateTeam(teamName),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Drag handle visual indicator
                    if (!isLocked) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Taşı",
                                tint = TextSecond,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (index < rankings.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(BorderColor.copy(alpha = 0.4f))
                    )
                }
            }

            // Error
            if (errorMsg != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = errorMsg,
                    color = RedError,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Save button
            if (!isLocked) {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BgDeep, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = BgDeep, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        val btnText = if (isSaved) {
                            if (hasNextGroup) "Güncelle ve İlerle" else "Güncelle"
                        } else {
                            if (hasNextGroup) "Tahmini Kaydet ve İlerle" else "Tahmini Kaydet"
                        }
                        Text(
                            text = btnText,
                            color = BgDeep,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Knockout Round Enum ──────────────────────────────────────────────────────
enum class KnockoutRound {
    ROUND_OF_32, ROUND_OF_16, QUARTER_FINALS, FINALS
}

// ── Knockout Prediction View ──────────────────────────────────────────────────
@Composable
fun KnockoutPredictionView(
    allEvents: List<MatchEvent>,
    predictions: Map<String, MatchPrediction>,
    username: String,
    viewMode: KnockoutViewMode,
    onViewModeChange: (KnockoutViewMode) -> Unit
) {
    val scope = rememberCoroutineScope()

    var groupPredictions by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var savedGroupKeys   by remember { mutableStateOf<Set<String>>(emptySet()) }
    var knockoutPreds    by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading        by remember { mutableStateOf(true) }
    var isSaving         by remember { mutableStateOf(false) }
    var isSaved          by remember { mutableStateOf(false) }
    var errorMsg         by remember { mutableStateOf<String?>(null) }
    var activeRoundTab   by remember { mutableStateOf(KnockoutRound.ROUND_OF_32) }

    // Load predictions from Firebase
    LaunchedEffect(username) {
        isLoading = true
        errorMsg = null
        try {
            // Load manual group predictions
            val groupFetched = PredictionService.getGroupPredictions(username)
            val initialGroups = mutableMapOf<String, List<String>>()
            val completedKeys = mutableSetOf<String>()
            worldCupGroups.forEach { group ->
                val encoded = group.label.replace(" ", "_").lowercase()
                val ranking = groupFetched[encoded]?.rankings?.takeIf { it.size == group.teams.size }
                if (ranking != null) {
                    initialGroups[encoded] = ranking
                    completedKeys.add(encoded)
                } else {
                    initialGroups[encoded] = group.teams.toList()
                }
            }
            groupPredictions = initialGroups
            savedGroupKeys = completedKeys

            // Load knockout predictions
            knockoutPreds = PredictionService.getKnockoutPredictions(username)
        } catch (e: Exception) {
            errorMsg = e.message ?: "Tahmin yükleme hatası"
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold, strokeWidth = 3.dp)
        }
        return
    }

    // Verify if all 12 group predictions are saved in Firebase
    // If not, we show a warning to complete them
    val completedGroupCount = savedGroupKeys.size
    val isGroupStageCompleted = completedGroupCount >= 12

    if (!isGroupStageCompleted) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .border(1.dp, RedError.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = RedError,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Grup Tahminleri Tamamlanmadı",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Eleme aşaması tahminlerine başlayabilmek için lütfen önce tüm 12 grubun sıralama tahminlerini 'Grup Tahmini' sekmesinden tamamlayın. (Tamamlanan: $completedGroupCount / 12)",
                    color = TextSecond,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // Calculate standings based on manual group predictions + predicted match scores
    val standings = remember(allEvents, predictions, groupPredictions) {
        val stats = calculatePredictedStandings(allEvents, predictions)
        val result = mutableMapOf<String, List<TeamStanding>>()
        
        worldCupGroups.forEach { group ->
            val encoded = group.label.replace(" ", "_").lowercase()
            val manualOrder = groupPredictions[encoded] ?: group.teams
            
            val orderedStandings = manualOrder.map { teamName ->
                val stat = stats[group.label]?.find { it.teamName == teamName }
                stat ?: TeamStanding(
                    teamName = teamName,
                    played = 0, won = 0, drawn = 0, lost = 0,
                    goalsFor = 0, goalsAgainst = 0, points = 0
                )
            }
            result[group.label] = orderedStandings
        }
        result
    }

    // Identify the top 8 third-placed teams
    val bestThirds = remember(standings) {
        val thirds = standings.values.mapNotNull { it.getOrNull(2) } // index 2 is 3rd place
        thirds.sortedWith(
            compareByDescending<TeamStanding> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
                .thenBy { it.teamName }
        ).take(8)
    }

    // Match the 8 third-placed teams to group winners using conflict-resolution
    val matchedThirds = remember(standings, bestThirds) {
        val winners = listOf("E Grubu", "I Grubu", "A Grubu", "L Grubu", "G Grubu", "D Grubu", "B Grubu", "K Grubu").map { groupLabel ->
            standings[groupLabel]?.getOrNull(0)?.teamName ?: ""
        }
        matchThirdPlaceTeams(winners, bestThirds.map { it.teamName })
    }

    // Resolve matches dynamically based on predictions
    val resolvedMatches = remember(standings, matchedThirds, knockoutPreds) {
        val matches = mutableMapOf<String, Pair<String, String>>()
        
        fun team(group: String, index: Int): String {
            return standings[group]?.getOrNull(index)?.teamName ?: ""
        }

        // Round of 32 (Matches 73 to 88)
        matches["73"] = team("A Grubu", 1) to team("B Grubu", 1) // A2 vs B2
        matches["74"] = team("C Grubu", 0) to team("F Grubu", 1) // C1 vs F2
        matches["75"] = team("E Grubu", 0) to (matchedThirds.getOrNull(0) ?: "") // E1 vs 3rd (A/B/C/D/F)
        matches["76"] = team("F Grubu", 0) to team("C Grubu", 1) // F1 vs C2
        matches["77"] = team("E Grubu", 1) to team("I Grubu", 1) // E2 vs I2
        matches["78"] = team("I Grubu", 0) to (matchedThirds.getOrNull(1) ?: "") // I1 vs 3rd (C/D/F/G/H)
        matches["79"] = team("A Grubu", 0) to (matchedThirds.getOrNull(2) ?: "") // A1 vs 3rd (C/E/F/H/I)
        matches["80"] = team("L Grubu", 0) to (matchedThirds.getOrNull(3) ?: "") // L1 vs 3rd (E/H/I/J/K)
        matches["81"] = team("G Grubu", 0) to (matchedThirds.getOrNull(4) ?: "") // G1 vs 3rd (A/E/H/I/J)
        matches["82"] = team("D Grubu", 0) to (matchedThirds.getOrNull(5) ?: "") // D1 vs 3rd (B/E/F/I/J)
        matches["83"] = team("H Grubu", 0) to team("J Grubu", 1) // H1 vs J2
        matches["84"] = team("K Grubu", 1) to team("L Grubu", 1) // K2 vs L2
        matches["85"] = team("B Grubu", 0) to (matchedThirds.getOrNull(6) ?: "") // B1 vs 3rd (E/F/G/I/J)
        matches["86"] = team("D Grubu", 1) to team("G Grubu", 1) // D2 vs G2
        matches["87"] = team("J Grubu", 0) to team("H Grubu", 1) // J1 vs H2
        matches["88"] = team("K Grubu", 0) to (matchedThirds.getOrNull(7) ?: "") // K1 vs 3rd (D/E/I/J/L)

        fun winner(id: String): String {
            return knockoutPreds[id]?.takeIf { it.isNotBlank() } ?: "$id. Maç Galibi"
        }

        fun loser(id: String): String {
            val w = knockoutPreds[id] ?: return "$id. Maç Mağlubu"
            val pair = matches[id] ?: return "$id. Maç Mağlubu"
            return when {
                w == pair.first -> pair.second.takeIf { it.isNotBlank() } ?: "$id. Maç Mağlubu"
                w == pair.second -> pair.first.takeIf { it.isNotBlank() } ?: "$id. Maç Mağlubu"
                else -> "$id. Maç Mağlubu"
            }
        }

        // Round of 16 (Matches 89 to 96)
        matches["89"] = winner("73") to winner("75")
        matches["90"] = winner("74") to winner("78")
        matches["91"] = winner("76") to winner("77")
        matches["92"] = winner("79") to winner("80")
        matches["93"] = winner("83") to winner("84")
        matches["94"] = winner("81") to winner("82")
        matches["95"] = winner("85") to winner("86")
        matches["96"] = winner("87") to winner("88")

        // Quarterfinals (Matches 97 to 100)
        matches["97"] = winner("89") to winner("90")
        matches["98"] = winner("91") to winner("92")
        matches["99"] = winner("93") to winner("94")
        matches["100"] = winner("95") to winner("96")

        // Semifinals (Matches 101 and 102)
        matches["101"] = winner("97") to winner("98")
        matches["102"] = winner("99") to winner("100")

        // 3rd Place & Final (Matches 103 and 104)
        matches["103"] = loser("101") to loser("102")
        matches["104"] = winner("101") to winner("102")

        matches
    }

    // Winner selection and downstream propagation cleaning
    val selectWinner: (String, String) -> Unit = { matchId, winner ->
        if (winner.isNotBlank() && !winner.contains("Galibi") && !winner.contains("Mağlubu")) {
            val newPreds = knockoutPreds.toMutableMap()
            newPreds[matchId] = winner
            
            val tempMatches = mutableMapOf<String, Pair<String, String>>()
            
            fun team(group: String, index: Int): String {
                return standings[group]?.getOrNull(index)?.teamName ?: ""
            }

            tempMatches["73"] = team("A Grubu", 1) to team("B Grubu", 1) // A2 vs B2
            tempMatches["74"] = team("C Grubu", 0) to team("F Grubu", 1) // C1 vs F2
            tempMatches["75"] = team("E Grubu", 0) to (matchedThirds.getOrNull(0) ?: "") // E1 vs 3rd
            tempMatches["76"] = team("F Grubu", 0) to team("C Grubu", 1) // F1 vs C2
            tempMatches["77"] = team("E Grubu", 1) to team("I Grubu", 1) // E2 vs I2
            tempMatches["78"] = team("I Grubu", 0) to (matchedThirds.getOrNull(1) ?: "") // I1 vs 3rd
            tempMatches["79"] = team("A Grubu", 0) to (matchedThirds.getOrNull(2) ?: "") // A1 vs 3rd
            tempMatches["80"] = team("L Grubu", 0) to (matchedThirds.getOrNull(3) ?: "") // L1 vs 3rd
            tempMatches["81"] = team("G Grubu", 0) to (matchedThirds.getOrNull(4) ?: "") // G1 vs 3rd
            tempMatches["82"] = team("D Grubu", 0) to (matchedThirds.getOrNull(5) ?: "") // D1 vs 3rd
            tempMatches["83"] = team("H Grubu", 0) to team("J Grubu", 1) // H1 vs J2
            tempMatches["84"] = team("K Grubu", 1) to team("L Grubu", 1) // K2 vs L2
            tempMatches["85"] = team("B Grubu", 0) to (matchedThirds.getOrNull(6) ?: "") // B1 vs 3rd
            tempMatches["86"] = team("D Grubu", 1) to team("G Grubu", 1) // D2 vs G2
            tempMatches["87"] = team("J Grubu", 0) to team("H Grubu", 1) // J1 vs H2
            tempMatches["88"] = team("K Grubu", 0) to (matchedThirds.getOrNull(7) ?: "") // K1 vs 3rd

            fun getW(id: String): String {
                return newPreds[id]?.takeIf { it.isNotBlank() } ?: "$id. Maç Galibi"
            }

            fun getL(id: String): String {
                val w = newPreds[id] ?: return "$id. Maç Mağlubu"
                val pair = tempMatches[id] ?: return "$id. Maç Mağlubu"
                return when {
                    w == pair.first -> pair.second.takeIf { it.isNotBlank() } ?: "$id. Maç Mağlubu"
                    w == pair.second -> pair.first.takeIf { it.isNotBlank() } ?: "$id. Maç Mağlubu"
                    else -> "$id. Maç Mağlubu"
                }
            }

            val pairMap = mapOf(
                "89" to ("73" to "75"), "90" to ("74" to "78"), "91" to ("76" to "77"), "92" to ("79" to "80"),
                "93" to ("83" to "84"), "94" to ("81" to "82"), "95" to ("85" to "86"), "96" to ("87" to "88")
            )
            val qMap = mapOf(
                "97" to ("89" to "90"), "98" to ("91" to "92"), "99" to ("93" to "94"), "100" to ("95" to "96")
            )
            val sfMap = mapOf(
                "101" to ("97" to "98"), "102" to ("99" to "100")
            )

            // Validate Son 32
            for (id in 73..88) {
                val sId = id.toString()
                val predVal = newPreds[sId]
                val pair = tempMatches[sId]
                if (predVal != null && pair != null) {
                    if (predVal != pair.first && predVal != pair.second) {
                        newPreds.remove(sId)
                    }
                }
            }

            // Validate Son 16
            pairMap.forEach { (id, parents) ->
                tempMatches[id] = getW(parents.first) to getW(parents.second)
                val predVal = newPreds[id]
                val pair = tempMatches[id]!!
                if (predVal != null) {
                    if (predVal != pair.first && predVal != pair.second || predVal.contains("Galibi")) {
                        newPreds.remove(id)
                    }
                }
            }

            // Validate Çeyrek
            qMap.forEach { (id, parents) ->
                tempMatches[id] = getW(parents.first) to getW(parents.second)
                val predVal = newPreds[id]
                val pair = tempMatches[id]!!
                if (predVal != null) {
                    if (predVal != pair.first && predVal != pair.second || predVal.contains("Galibi")) {
                        newPreds.remove(id)
                    }
                }
            }

            // Validate Yarı
            sfMap.forEach { (id, parents) ->
                tempMatches[id] = getW(parents.first) to getW(parents.second)
                val predVal = newPreds[id]
                val pair = tempMatches[id]!!
                if (predVal != null) {
                    if (predVal != pair.first && predVal != pair.second || predVal.contains("Galibi")) {
                        newPreds.remove(id)
                    }
                }
            }

            // Validate 3rd and Final
            tempMatches["103"] = getL("101") to getL("102")
            val p103 = newPreds["103"]
            val pair103 = tempMatches["103"]!!
            if (p103 != null) {
                if (p103 != pair103.first && p103 != pair103.second || p103.contains("Mağlubu")) {
                    newPreds.remove("103")
                }
            }

            tempMatches["104"] = getW("101") to getW("102")
            val p104 = newPreds["104"]
            val pair104 = tempMatches["104"]!!
            if (p104 != null) {
                if (p104 != pair104.first && p104 != pair104.second || p104.contains("Galibi")) {
                    newPreds.remove("104")
                }
            }

            knockoutPreds = newPreds
            isSaved = false
        }
    }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Info Box & Switcher Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Switcher (Visual vs List)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val modes = listOf(KnockoutViewMode.BRACKET to "Şematik Görünüm", KnockoutViewMode.LIST to "Liste Görünümü")
                modes.forEach { (mode, label) ->
                    val isActive = viewMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive) Gold else Color.Transparent)
                            .clickable { onViewModeChange(mode) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isActive) BgDeep else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isSaved) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Kaydedildi", tint = GreenLive, modifier = Modifier.size(16.dp))
                    Text("Değişiklikler Kaydedildi", color = GreenLive, fontSize = 11.sp)
                }
            }
        }

        // Info details description
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Gold.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = GoldGlow)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(22.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Eleme Aşaması Tahminleri",
                        color = Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Takımlara tıklayarak kazananı seçin. Seçtiğiniz takım otomatik olarak bir sonraki tura yükselir. " +
                        "Şematik şablon üzerinde tüm turları görebilir, dilerseniz Liste Görünümü üzerinden tur bazlı listeleyebilirsiniz.",
                        color = TextSecond,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (viewMode == KnockoutViewMode.BRACKET) {
            // Full horizontal-scrollable visual bracket tree layout (karşılıklı şematik görünüm)
            val bracketScrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(bracketScrollState)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    val leftR32 = listOf("73", "75", "74", "78", "76", "77", "79", "80")
                    val leftR16 = listOf("89", "90", "91", "92")
                    val leftQF = listOf("97", "98")
                    val leftSF = listOf("101")

                    val rightR32 = listOf("83", "84", "81", "82", "85", "86", "87", "88")
                    val rightR16 = listOf("93", "94", "95", "96")
                    val rightQF = listOf("99", "100")
                    val rightSF = listOf("102")

                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // 1. Left Round of 32
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            leftR32.forEach { id ->
                                val pair = resolvedMatches[id] ?: ("" to "")
                                KnockoutBracketMatchCard(
                                    matchId = id,
                                    homeTeam = pair.first,
                                    awayTeam = pair.second,
                                    selectedWinner = knockoutPreds[id] ?: "",
                                    onSelectWinner = { selectWinner(id, it) }
                                )
                            }
                        }

                        // 2. Connectors Left R32 -> R16
                        Column {
                            repeat(4) {
                                BracketConnector(height = 192.dp, topY = 40.dp, botY = 136.dp, midY = 88.dp, isMirrored = false)
                            }
                        }

                        // 3. Left Round of 16
                        Column {
                            Spacer(Modifier.height(48.dp))
                            leftR16.forEachIndexed { idx, id ->
                                val pair = resolvedMatches[id] ?: ("" to "")
                                KnockoutBracketMatchCard(
                                    matchId = id,
                                    homeTeam = pair.first,
                                    awayTeam = pair.second,
                                    selectedWinner = knockoutPreds[id] ?: "",
                                    onSelectWinner = { selectWinner(id, it) }
                                )
                                if (idx < leftR16.lastIndex) {
                                    Spacer(Modifier.height(112.dp))
                                }
                            }
                        }

                        // 4. Connectors Left R16 -> QF
                        Column {
                            repeat(2) {
                                BracketConnector(height = 384.dp, topY = 88.dp, botY = 280.dp, midY = 184.dp, isMirrored = false)
                            }
                        }

                        // 5. Left Quarterfinals
                        Column {
                            Spacer(Modifier.height(144.dp))
                            leftQF.forEachIndexed { idx, id ->
                                val pair = resolvedMatches[id] ?: ("" to "")
                                KnockoutBracketMatchCard(
                                    matchId = id,
                                    homeTeam = pair.first,
                                    awayTeam = pair.second,
                                    selectedWinner = knockoutPreds[id] ?: "",
                                    onSelectWinner = { selectWinner(id, it) }
                                )
                                if (idx < leftQF.lastIndex) {
                                    Spacer(Modifier.height(304.dp))
                                }
                            }
                        }

                        // 6. Connectors Left QF -> SF
                        Column {
                            BracketConnector(height = 768.dp, topY = 184.dp, botY = 568.dp, midY = 376.dp, isMirrored = false)
                        }

                        // 7. Left Semifinal
                        Column {
                            Spacer(Modifier.height(336.dp))
                            val id = leftSF[0]
                            val pair = resolvedMatches[id] ?: ("" to "")
                            KnockoutBracketMatchCard(
                                matchId = id,
                                homeTeam = pair.first,
                                awayTeam = pair.second,
                                selectedWinner = knockoutPreds[id] ?: "",
                                onSelectWinner = { selectWinner(id, it) }
                            )
                        }

                        // 8. Center Podiums & Trophy Column
                        Column(
                            modifier = Modifier.width(220.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(16.dp))

                            // World Champion podium at the top
                            val champ = knockoutPreds["104"] ?: ""
                            ChampionPodium(winner = champ)

                            Spacer(Modifier.height(30.dp))

                            // Final Match Card (Match 104)
                            Text(
                                "FİNAL",
                                color = Gold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            val finalPair = resolvedMatches["104"] ?: ("" to "")
                            KnockoutBracketMatchCard(
                                matchId = "104",
                                homeTeam = finalPair.first,
                                awayTeam = finalPair.second,
                                selectedWinner = knockoutPreds["104"] ?: "",
                                onSelectWinner = { selectWinner("104", it) }
                            )

                            Spacer(Modifier.height(24.dp))

                            // Trophy Image badge URL from sports DB
                            coil3.compose.AsyncImage(
                                model = "https://r2.thesportsdb.com/images/media/league/badge/e7er5g1696521789.png",
                                contentDescription = "Dünya Kupası",
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier.size(100.dp)
                            )

                            Spacer(Modifier.height(24.dp))

                            // Third Place Match Card (Match 103)
                            Text(
                                "ÜÇÜNCÜLÜK MAÇI",
                                color = BlueAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            val thirdPair = resolvedMatches["103"] ?: ("" to "")
                            KnockoutBracketMatchCard(
                                matchId = "103",
                                homeTeam = thirdPair.first,
                                awayTeam = thirdPair.second,
                                selectedWinner = knockoutPreds["103"] ?: "",
                                onSelectWinner = { selectWinner("103", it) }
                            )

                            Spacer(Modifier.height(30.dp))

                            // Third place podium at the bottom
                            val thirdPlace = knockoutPreds["103"] ?: ""
                            ThirdPlacePodium(winner = thirdPlace)
                        }

                        // 9. Right Semifinal
                        Column {
                            Spacer(Modifier.height(336.dp))
                            val id = rightSF[0]
                            val pair = resolvedMatches[id] ?: ("" to "")
                            KnockoutBracketMatchCard(
                                matchId = id,
                                homeTeam = pair.first,
                                awayTeam = pair.second,
                                selectedWinner = knockoutPreds[id] ?: "",
                                onSelectWinner = { selectWinner(id, it) }
                            )
                        }

                        // 10. Connectors Mirrored QF -> SF
                        Column {
                            BracketConnector(height = 768.dp, topY = 184.dp, botY = 568.dp, midY = 376.dp, isMirrored = true)
                        }

                        // 11. Right Quarterfinals
                        Column {
                            Spacer(Modifier.height(144.dp))
                            rightQF.forEachIndexed { idx, id ->
                                val pair = resolvedMatches[id] ?: ("" to "")
                                KnockoutBracketMatchCard(
                                    matchId = id,
                                    homeTeam = pair.first,
                                    awayTeam = pair.second,
                                    selectedWinner = knockoutPreds[id] ?: "",
                                    onSelectWinner = { selectWinner(id, it) }
                                )
                                if (idx < rightQF.lastIndex) {
                                    Spacer(Modifier.height(304.dp))
                                }
                            }
                        }

                        // 12. Connectors Mirrored R16 -> QF
                        Column {
                            repeat(2) {
                                BracketConnector(height = 384.dp, topY = 88.dp, botY = 280.dp, midY = 184.dp, isMirrored = true)
                            }
                        }

                        // 13. Right Round of 16
                        Column {
                            Spacer(Modifier.height(48.dp))
                            rightR16.forEachIndexed { idx, id ->
                                val pair = resolvedMatches[id] ?: ("" to "")
                                KnockoutBracketMatchCard(
                                    matchId = id,
                                    homeTeam = pair.first,
                                    awayTeam = pair.second,
                                    selectedWinner = knockoutPreds[id] ?: "",
                                    onSelectWinner = { selectWinner(id, it) }
                                )
                                if (idx < rightR16.lastIndex) {
                                    Spacer(Modifier.height(112.dp))
                                }
                            }
                        }

                        // 14. Connectors Mirrored R32 -> R16
                        Column {
                            repeat(4) {
                                BracketConnector(height = 192.dp, topY = 40.dp, botY = 136.dp, midY = 88.dp, isMirrored = true)
                            }
                        }

                        // 15. Right Round of 32
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            rightR32.forEach { id ->
                                val pair = resolvedMatches[id] ?: ("" to "")
                                KnockoutBracketMatchCard(
                                    matchId = id,
                                    homeTeam = pair.first,
                                    awayTeam = pair.second,
                                    selectedWinner = knockoutPreds[id] ?: "",
                                    onSelectWinner = { selectWinner(id, it) }
                                )
                            }
                        }
                    }
                }

                HorizontalScrollbar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    adapter = rememberScrollbarAdapter(bracketScrollState),
                    style = ScrollbarStyle(
                        minimalHeight = 8.dp,
                        thickness = 8.dp,
                        shape = RoundedCornerShape(4.dp),
                        hoverDurationMillis = 250,
                        unhoverColor = Color.White.copy(alpha = 0.18f),
                        hoverColor = Gold.copy(alpha = 0.75f)
                    )
                )
            }
        } else {
            // Existing stepper navigation tabs for rounds (Only shown in List Mode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KnockoutRound.entries.forEach { round ->
                    val label = when (round) {
                        KnockoutRound.ROUND_OF_32  -> "Son 32"
                        KnockoutRound.ROUND_OF_16  -> "Son 16"
                        KnockoutRound.QUARTER_FINALS -> "Çeyrek Final"
                        KnockoutRound.FINALS         -> "Final"
                    }
                    val isActive = round == activeRoundTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isActive) Gold else Color.Transparent)
                            .clickable { activeRoundTab = round }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isActive) BgDeep else TextSecond,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Active Round matches (Only shown in List Mode)
            val activeMatches = remember(activeRoundTab, resolvedMatches) {
                when (activeRoundTab) {
                    KnockoutRound.ROUND_OF_32 -> (73..88).map { it.toString() }
                    KnockoutRound.ROUND_OF_16 -> (89..96).map { it.toString() }
                    KnockoutRound.QUARTER_FINALS -> (97..100).map { it.toString() }
                    KnockoutRound.FINALS -> listOf("101", "102", "103", "104")
                }
            }

            activeMatches.forEach { matchId ->
                val pair = resolvedMatches[matchId] ?: ("" to "")
                val winner = knockoutPreds[matchId] ?: ""
                val title = when (matchId) {
                    "103" -> "Üçüncülük Maçı"
                    "104" -> "DÜNYA KUPASI FİNALİ"
                    else -> "${matchId}. Maç"
                }
                
                KnockoutMatchCard(
                    matchId = matchId,
                    title = title,
                    homeTeam = pair.first,
                    awayTeam = pair.second,
                    selectedWinner = winner,
                    onSelectWinner = { selectWinner(matchId, it) }
                )
            }
        }

        // Error Msg
        if (errorMsg != null) {
            Text(
                text = errorMsg!!,
                color = RedError,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        // Save Button
        Button(
            onClick = {
                scope.launch {
                    isSaving = true
                    errorMsg = null
                    try {
                        PredictionService.saveKnockoutPredictions(username, knockoutPreds)
                        isSaved = true
                    } catch (e: Exception) {
                        errorMsg = e.message ?: "Kayıt hatası"
                    } finally {
                        isSaving = false
                    }
                }
            },
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = Gold),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BgDeep, strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (isSaved) Icons.Default.CheckCircle else Icons.Default.Save,
                    contentDescription = null,
                    tint = BgDeep,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isSaved) "Tüm Eleme Tahminleri Kaydedildi!" else "Eleme Tahminlerini Kaydet",
                    color = BgDeep,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Knockout Match Card Composable ──────────────────────────────────────────
@Composable
fun KnockoutMatchCard(
    matchId: String,
    title: String,
    homeTeam: String,
    awayTeam: String,
    selectedWinner: String,
    onSelectWinner: (String) -> Unit
) {
    val isHomePlaceholder = homeTeam.contains("Galibi") || homeTeam.contains("Mağlubu") || homeTeam.isBlank()
    val isAwayPlaceholder = awayTeam.contains("Galibi") || awayTeam.contains("Mağlubu") || awayTeam.isBlank()
    val isWinnerHome = selectedWinner.isNotBlank() && selectedWinner == homeTeam
    val isWinnerAway = selectedWinner.isNotBlank() && selectedWinner == awayTeam

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Match title
            Text(
                text = title,
                color = if (matchId == "104") Gold else TextSecond,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            // Teams Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Home Team Slot
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isWinnerHome -> Gold.copy(alpha = 0.12f)
                                else -> Color.Transparent
                            }
                        )
                        .border(
                            1.dp,
                            if (isWinnerHome) Gold.copy(alpha = 0.6f) else BorderColor,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = !isHomePlaceholder) { onSelectWinner(homeTeam) }
                        .padding(10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isHomePlaceholder) {
                            TeamBadge(name = translateTeam(homeTeam), badgeUrl = getTeamFlagUrl(homeTeam))
                            Text(
                                text = translateTeam(homeTeam),
                                color = if (isWinnerHome) Gold else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isWinnerHome) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = TextSecond.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = homeTeam.ifBlank { "Bekliyor" },
                                color = TextSecond.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Text(
                    text = "VS",
                    color = TextSecond.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                // Away Team Slot
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isWinnerAway -> Gold.copy(alpha = 0.12f)
                                else -> Color.Transparent
                            }
                        )
                        .border(
                            1.dp,
                            if (isWinnerAway) Gold.copy(alpha = 0.6f) else BorderColor,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(enabled = !isAwayPlaceholder) { onSelectWinner(awayTeam) }
                        .padding(10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isAwayPlaceholder) {
                            TeamBadge(name = translateTeam(awayTeam), badgeUrl = getTeamFlagUrl(awayTeam))
                            Text(
                                text = translateTeam(awayTeam),
                                color = if (isWinnerAway) Gold else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isWinnerAway) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = TextSecond.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = awayTeam.ifBlank { "Bekliyor" },
                                color = TextSecond.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Knockout View Mode ────────────────────────────────────────────────────────
enum class KnockoutViewMode { BRACKET, LIST }

// ── Bracket Connector Composable ──────────────────────────────────────────────
@Composable
fun BracketConnector(
    height: androidx.compose.ui.unit.Dp,
    topY: androidx.compose.ui.unit.Dp,
    botY: androidx.compose.ui.unit.Dp,
    midY: androidx.compose.ui.unit.Dp,
    isMirrored: Boolean,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier.width(24.dp).height(height)) {
        val w = size.width
        val h = size.height
        val tY = topY.toPx()
        val bY = botY.toPx()
        val mY = midY.toPx()
        val midX = w / 2

        val lineColor = Color(0x33FFFFFF) // Subtle gray border/line
        val circleColor = Color(0x99F5C942) // Gold glowing indicator dot
        val strokeW = 1.5.dp.toPx()

        if (!isMirrored) {
            // Left to Right path
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(0f, tY), end = androidx.compose.ui.geometry.Offset(midX, tY), strokeWidth = strokeW)
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(0f, bY), end = androidx.compose.ui.geometry.Offset(midX, bY), strokeWidth = strokeW)
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(midX, tY), end = androidx.compose.ui.geometry.Offset(midX, bY), strokeWidth = strokeW)
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(midX, mY), end = androidx.compose.ui.geometry.Offset(w, mY), strokeWidth = strokeW)
            drawCircle(circleColor, radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(midX, mY))
        } else {
            // Right to Left path
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(w, tY), end = androidx.compose.ui.geometry.Offset(midX, tY), strokeWidth = strokeW)
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(w, bY), end = androidx.compose.ui.geometry.Offset(midX, bY), strokeWidth = strokeW)
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(midX, tY), end = androidx.compose.ui.geometry.Offset(midX, bY), strokeWidth = strokeW)
            drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(midX, mY), end = androidx.compose.ui.geometry.Offset(0f, mY), strokeWidth = strokeW)
            drawCircle(circleColor, radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(midX, mY))
        }
    }
}

// ── Knockout Bracket Match Card ───────────────────────────────────────────────
@Composable
fun KnockoutBracketMatchCard(
    matchId: String,
    homeTeam: String,
    awayTeam: String,
    selectedWinner: String,
    onSelectWinner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHomePlaceholder = homeTeam.contains("Galibi") || homeTeam.contains("Mağlubu") || homeTeam.isBlank()
    val isAwayPlaceholder = awayTeam.contains("Galibi") || awayTeam.contains("Mağlubu") || awayTeam.isBlank()
    val isWinnerHome = selectedWinner.isNotBlank() && selectedWinner == homeTeam
    val isWinnerAway = selectedWinner.isNotBlank() && selectedWinner == awayTeam

    Card(
        modifier = modifier
            .width(150.dp)
            .height(80.dp)
            .border(1.dp, if (isWinnerHome || isWinnerAway) GoldDim else BorderColor, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Home Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (isWinnerHome) Gold.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(enabled = !isHomePlaceholder) { onSelectWinner(homeTeam) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isHomePlaceholder) {
                    TeamBadgeMini(name = translateTeam(homeTeam), badgeUrl = getTeamFlagUrl(homeTeam))
                    Text(
                        text = translateTeam(homeTeam),
                        color = if (isWinnerHome) Gold else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (isWinnerHome) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isWinnerHome) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = TextSecond.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = homeTeam.ifBlank { "Bekliyor" },
                        color = TextSecond.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(BorderColor))

            // Away Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(if (isWinnerAway) Gold.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(enabled = !isAwayPlaceholder) { onSelectWinner(awayTeam) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isAwayPlaceholder) {
                    TeamBadgeMini(name = translateTeam(awayTeam), badgeUrl = getTeamFlagUrl(awayTeam))
                    Text(
                        text = translateTeam(awayTeam),
                        color = if (isWinnerAway) Gold else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (isWinnerAway) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isWinnerAway) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = TextSecond.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = awayTeam.ifBlank { "Bekliyor" },
                        color = TextSecond.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ── Mini Team Badge ───────────────────────────────────────────────────────────
@Composable
fun TeamBadgeMini(name: String, badgeUrl: String?) {
    if (!badgeUrl.isNullOrBlank()) {
        coil3.compose.AsyncImage(
            model = badgeUrl,
            contentDescription = name,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BgGlass)
                .border(0.5.dp, BorderColor, RoundedCornerShape(3.dp))
                .padding(1.dp)
        )
    } else {
        val initials = name.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.linearGradient(listOf(GoldDim, Gold))),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = BgDeep, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Podium Composables ────────────────────────────────────────────────────────
@Composable
fun ChampionPodium(winner: String) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .border(2.dp, Gold, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GoldGlow)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "DÜNYA ŞAMPİYONU",
                color = Gold,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            if (winner.isNotBlank() && !winner.contains("Galibi")) {
                TeamBadgePodium(name = translateTeam(winner), badgeUrl = getTeamFlagUrl(winner), size = 64.dp)
                Text(
                    translateTeam(winner).uppercase(),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            } else {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = Gold.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    "BEKLİYOR",
                    color = Gold.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ThirdPlacePodium(winner: String) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .border(1.dp, BlueAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "ÜÇÜNCÜ",
                color = BlueAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            if (winner.isNotBlank() && !winner.contains("Mağlubu")) {
                TeamBadgePodium(name = translateTeam(winner), badgeUrl = getTeamFlagUrl(winner), size = 48.dp)
                Text(
                    translateTeam(winner),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = TextSecond.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Bekliyor",
                    color = TextSecond.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun TeamBadgePodium(name: String, badgeUrl: String?, size: androidx.compose.ui.unit.Dp) {
    if (!badgeUrl.isNullOrBlank()) {
        coil3.compose.AsyncImage(
            model = badgeUrl,
            contentDescription = name,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
        )
    }
}

@Composable
private fun GuestUpgradeBanner(onUpgradeClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable { onUpgradeClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = GoldGlow)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(24.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Geçici Misafir Hesabı",
                        color = Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tahminlerinizi kalıcı olarak saklamak ve liderlik tablosunda yer almak için şimdi üye olun!",
                        color = TextSecond,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onUpgradeClick,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Kayıt Ol",
                    color = BgDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GuestUpgradeDialog(
    guestUsername: String,
    onDismiss: () -> Unit,
    onUpgradeSuccess: (String) -> Unit
) {
    var usernameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Kayıt Ol & Tahminleri Aktar",
                color = Gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Lütfen hesap bilgilerinizi girin. Mevcut tüm tahminleriniz bu hesaba aktarılacaktır.",
                    color = TextSecond,
                    fontSize = 12.sp
                )
                
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = RedError,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it; errorMessage = null },
                    label = { Text("Kullanıcı Adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold,
                        focusedLabelColor = Gold,
                        unfocusedLabelColor = TextSecond
                    )
                )
                
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it; errorMessage = null },
                    label = { Text("E-posta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold,
                        focusedLabelColor = Gold,
                        unfocusedLabelColor = TextSecond
                    )
                )
                
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it; errorMessage = null },
                    label = { Text("Şifre") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Gold,
                        focusedLabelColor = Gold,
                        unfocusedLabelColor = TextSecond
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (usernameInput.isBlank() || emailInput.isBlank() || passwordInput.isBlank()) {
                        errorMessage = "Lütfen tüm alanları doldurun."
                        return@Button
                    }
                    if (usernameInput.trim().lowercase().startsWith("misafir")) {
                        errorMessage = "Kullanıcı adı 'misafir' ile başlayamaz."
                        return@Button
                    }
                    isLoading = true
                    scope.launch {
                        try {
                            // 1. Register new user
                            val registerOk = AuthService.registerUser(usernameInput, emailInput, passwordInput)
                            if (registerOk) {
                                // 2. Migrate predictions
                                val targetUser = usernameInput.trim()
                                AuthService.migrateGuestPredictions(guestUsername, targetUser)
                                // 3. Callback success
                                onUpgradeSuccess(targetUser)
                            } else {
                                errorMessage = "Kayıt sırasında hata oluştu."
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "İşlem başarısız oldu."
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = BgDeep, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Kaydet ve Aktar", color = BgDeep, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("İptal", color = TextSecond)
            }
        },
        containerColor = BgCard,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
fun NotificationPreferencesDialog(
    username: String,
    allEvents: List<MatchEvent>,
    onDismiss: () -> Unit
) {
    var selectedTeams by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(username) {
        NotificationService.getSubscribedTeams(username) { teams ->
            selectedTeams = teams.map { it.lowercase().trim() }.toSet()
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = Gold)
                Text("Bildirim Tercihleri", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Aşağıdan bildirim almak istediğiniz takımları seçebilirsiniz. Seçtiğiniz takımların maçları başlamadan 1 saat önce bildirim gönderilecektir.",
                    color = TextSecond,
                    fontSize = 11.sp
                )
                
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        worldCupGroups.forEach { group ->
                            item {
                                Text(
                                    text = group.label,
                                    color = Gold.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(group.teams) { team ->
                                val translated = translateTeam(team)
                                val isSelected = team.lowercase().trim() in selectedTeams
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedTeams = if (isSelected) {
                                                selectedTeams - team.lowercase().trim()
                                            } else {
                                                selectedTeams + team.lowercase().trim()
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selectedTeams = if (isSelected) {
                                                selectedTeams - team.lowercase().trim()
                                            } else {
                                                selectedTeams + team.lowercase().trim()
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Gold,
                                            uncheckedColor = TextSecond,
                                            checkmarkColor = BgDeep
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TeamBadge(name = translated, badgeUrl = getTeamFlagUrl(team))
                                    Spacer(Modifier.width(8.dp))
                                    Text(translated, color = TextPrimary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSaving = true
                    scope.launch {
                        NotificationService.saveSubscribedTeams(username, selectedTeams.toList())
                        // Seçilen takımların en yakın yaklaşan maçı için hemen yerel bildirim tetikle
                        notifyNearestMatch(selectedTeams, allEvents)
                        isSaving = false
                        onDismiss()
                    }
                },
                enabled = !isLoading && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = BgDeep, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Kaydet", color = BgDeep, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("İptal", color = TextSecond)
            }
        },
        containerColor = BgCard,
        shape = RoundedCornerShape(14.dp)
    )
}

private fun notifyNearestMatch(selectedTeams: Set<String>, allEvents: List<MatchEvent>) {
    if (selectedTeams.isEmpty() || allEvents.isEmpty()) return
    
    val now = kotlin.time.Clock.System.now()
    
    // Seçilen takımların yaklaşan maçlarını filtrele
    val upcomingMatches = allEvents.filter { evt ->
        val home = evt.strHomeTeam?.lowercase()?.trim() ?: ""
        val away = evt.strAwayTeam?.lowercase()?.trim() ?: ""
        (home in selectedTeams || away in selectedTeams) && matchStatus(evt) == MatchStatus.UPCOMING
    }
    
    // En yakın maçı bul
    val nearestMatch = upcomingMatches
        .mapNotNull { evt -> parseMatchKickoffInstant(evt)?.let { it to evt } }
        .minByOrNull { it.first }
        ?.second
        
    if (nearestMatch != null) {
        val kickoff = parseMatchKickoffInstant(nearestMatch)
        if (kickoff != null) {
            val duration = kickoff - now
            val days = duration.inWholeDays
            val hours = (duration.inWholeHours % 24)
            val minutes = (duration.inWholeMinutes % 60)
            
            val timeStr = when {
                days > 0 -> "$days gün $hours saat"
                hours > 0 -> "$hours saat $minutes dakika"
                else -> "$minutes dakika"
            }
            
            val homeTranslated = translateTeam(nearestMatch.strHomeTeam ?: "")
            val awayTranslated = translateTeam(nearestMatch.strAwayTeam ?: "")
            
            NotificationService.showLocalNotification(
                "Bildirim Tercihleriniz Kaydedildi! 🔔",
                "En yakın maç ($homeTranslated - $awayTranslated) $timeStr sonra başlayacak."
            )
        }
    }
}




