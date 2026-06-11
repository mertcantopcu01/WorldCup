package com.mert.dbrstudios.worldcup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mert.dbrstudios.worldcup.data.model.MatchEvent
import com.mert.dbrstudios.worldcup.ui.theme.*
import com.mert.dbrstudios.worldcup.viewmodel.MatchStatus
import com.mert.dbrstudios.worldcup.viewmodel.matchStatus
import com.mert.dbrstudios.worldcup.viewmodel.roundLabel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun MatchCard(evt: MatchEvent, modifier: Modifier = Modifier) {
    val status = matchStatus(evt)

    val borderColor = when (status) {
        MatchStatus.LIVE     -> BorderLive
        MatchStatus.DONE     -> BorderColor
        MatchStatus.UPCOMING -> BorderColor
    }

    val cardBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF0D1321), Color(0xFF111827))
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBrush)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        // Gold shimmer top edge for upcoming
        if (status == MatchStatus.UPCOMING) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, GoldGlow, Color.Transparent)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Round label + Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = roundLabel(evt.intRound).uppercase(),
                    color = GoldDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                StatusBadge(status = status, timeLocal = evt.strTimeLocal)
            }

            // Teams row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home team
                TeamSection(
                    name = evt.strHomeTeam ?: "–",
                    badgeUrl = evt.strHomeTeamBadge,
                    isHome = true,
                    isDone = status == MatchStatus.DONE,
                    modifier = Modifier.weight(1f)
                )

                // Score / VS
                ScoreCenter(
                    homeScore = evt.intHomeScore,
                    awayScore = evt.intAwayScore,
                    status = status,
                    modifier = Modifier.width(72.dp)
                )

                // Away team
                TeamSection(
                    name = evt.strAwayTeam ?: "–",
                    badgeUrl = evt.strAwayTeamBadge,
                    isHome = false,
                    isDone = status == MatchStatus.DONE,
                    modifier = Modifier.weight(1f)
                )
            }

            // Venue
            if (!evt.strVenue.isNullOrBlank()) {
                Text(
                    text = "📍  ${evt.strVenue}",
                    color = TextSecond,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Team column ───────────────────────────────────────────────────────────────

@Composable
private fun TeamSection(
    name: String,
    badgeUrl: String?,
    isHome: Boolean,
    isDone: Boolean,
    modifier: Modifier = Modifier
) {
    val nameColor = if (isDone) TextSecond else TextPrimary
    val align = if (isHome) Alignment.End else Alignment.Start

    Column(
        modifier = modifier,
        horizontalAlignment = align,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Badge
        if (!badgeUrl.isNullOrBlank()) {
            AsyncImage(
                model = badgeUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BgGlass)
                    .border(1.dp, BorderColor, CircleShape)
                    .padding(4.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BgGlass)
                    .border(1.dp, BorderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("⚽", fontSize = 18.sp)
            }
        }
        Text(
            text = name,
            color = nameColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isHome) TextAlign.End else TextAlign.Start
        )
    }
}

// ── Center score / vs ─────────────────────────────────────────────────────────

@Composable
private fun ScoreCenter(
    homeScore: String?,
    awayScore: String?,
    status: MatchStatus,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (status == MatchStatus.DONE && homeScore != null && awayScore != null) {
            Text(
                text = "$homeScore – $awayScore",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        } else {
            Text(
                text = "VS",
                color = TextSecond,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

// ── Status Badge ──────────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(status: MatchStatus, timeLocal: String?) {
    val (text, bg, fg) = when (status) {
        MatchStatus.LIVE -> Triple("● CANLI", Color(0x1F00E87A), GreenLive)
        MatchStatus.DONE -> Triple("BİTTİ",   Color(0x0FFFFFFF),  TextSecond)
        MatchStatus.UPCOMING -> {
            val label = formatLocalTime(timeLocal)
            Triple(label, Color(0x1F3B82F6), BlueAccent)
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(40.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(40.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

private fun formatLocalTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "–"
    return try {
        val t = LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm:ss"))
        t.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) { raw }
}
