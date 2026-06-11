package com.mert.dbrstudios.worldcup.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mert.dbrstudios.worldcup.ui.components.DateHeader
import com.mert.dbrstudios.worldcup.ui.components.MatchCard
import com.mert.dbrstudios.worldcup.ui.theme.*
import com.mert.dbrstudios.worldcup.viewmodel.FilterType
import com.mert.dbrstudios.worldcup.viewmodel.FixtureUiState
import com.mert.dbrstudios.worldcup.viewmodel.FixtureViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixtureScreen(vm: FixtureViewModel = viewModel()) {
    val uiState     by vm.uiState.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val activeFilter by vm.activeFilter.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            // Hero header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0A1220), BgDeep),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "🏆", fontSize = 36.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "FIFA World Cup 2026",
                        color = Gold,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Kanada · ABD · Meksika",
                        color = TextSecond,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // ── Stats bar ──────────────────────────────────────────────────
            if (uiState is FixtureUiState.Success) {
                item {
                    StatsBar(vm = vm)
                    Spacer(Modifier.height(12.dp))
                }
            }

            // ── Search bar ─────────────────────────────────────────────────
            item {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery,
                    onClear = { vm.setSearchQuery(""); focusManager.clearFocus() }
                )
                Spacer(Modifier.height(10.dp))
            }

            // ── Filter chips ───────────────────────────────────────────────
            item {
                FilterChipRow(activeFilter = activeFilter, onFilterChange = vm::setFilter)
                Spacer(Modifier.height(16.dp))
            }

            // ── Main content ───────────────────────────────────────────────
            when (val state = uiState) {
                is FixtureUiState.Loading -> {
                    item { LoadingContent() }
                }
                is FixtureUiState.Error -> {
                    item { ErrorContent(message = state.message, onRetry = vm::loadFixtures) }
                }
                is FixtureUiState.Success -> {
                    if (state.groupedMatches.isEmpty()) {
                        item { EmptyContent() }
                    } else {
                        state.groupedMatches.forEach { (date, matches) ->
                            item(key = "header_$date") {
                                DateHeader(
                                    dateKey = date,
                                    matchCount = matches.size,
                                    modifier = Modifier.padding(top = if (date == state.groupedMatches.keys.first()) 0.dp else 12.dp)
                                )
                            }
                            items(items = matches, key = { it.idEvent }) { evt ->
                                MatchCard(
                                    evt = evt,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .animateItem()
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
            item {
                Text(
                    text = "Veriler TheSportsDB tarafından sağlanmaktadır",
                    color = TextSecond.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

// ── Stats Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun StatsBar(vm: FixtureViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatChip(label = "Toplam",    value = vm.totalCount.toString(), modifier = Modifier.weight(1f))
        StatChip(label = "Tamamlanan", value = vm.doneCount.toString(),  modifier = Modifier.weight(1f))
        StatChip(label = "Bekleyen",  value = vm.upcomingCount.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BgCard)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = Gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                color = TextSecond,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── Search Bar ────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text("Takım ara…", color = TextSecond, fontSize = 14.sp)
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecond)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Temizle", tint = TextSecond)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(40.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { }),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = GoldDim,
            unfocusedBorderColor = BorderColor,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
            cursorColor          = Gold,
            focusedContainerColor   = BgCard,
            unfocusedContainerColor = BgCard
        )
    )
}

// ── Filter Chips ──────────────────────────────────────────────────────────────

@Composable
private fun FilterChipRow(activeFilter: FilterType, onFilterChange: (FilterType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterType.entries.forEach { filter ->
            val label = when (filter) {
                FilterType.ALL      -> "Tümü"
                FilterType.UPCOMING -> "Bekleyen"
                FilterType.DONE     -> "Tamamlanan"
            }
            val isActive = filter == activeFilter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(40.dp))
                    .background(if (isActive) Gold else BgCard)
                    .clickable { onFilterChange(filter) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isActive) BgDeep else TextSecond,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ── Loading / Error / Empty ───────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = Gold, strokeWidth = 3.dp)
            Text("Fikstür yükleniyor…", color = TextSecond, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("⚠️", fontSize = 40.sp)
            Text(
                text = "Veri yüklenemedi",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = TextSecond,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Gold),
                shape = RoundedCornerShape(40.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = BgDeep)
                Spacer(Modifier.width(8.dp))
                Text("Tekrar Dene", color = BgDeep, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("🔍", fontSize = 40.sp)
            Text(
                text = "Aramanıza uygun maç bulunamadı",
                color = TextSecond,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
