@file:OptIn(kotlin.time.ExperimentalTime::class)

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
fun MatchCard(
    evt: MatchEvent,
    prediction: MatchPrediction?,
    onPredictClick: (() -> Unit)?,
    onQuickPredict: ((home: Int, away: Int) -> Unit)? = null,
    showDate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val status = matchStatus(evt)
    var timeTick by remember { mutableStateOf(0) }

    LaunchedEffect(evt.idEvent) {
        while (true) {
            delay(30_000)
            timeTick++
        }
    }

    val predictionsLocked = remember(timeTick) { isPredictionLocked(evt) }
    val lockMessage = remember(timeTick) { predictionLockMessage(evt) }
    val canPredict = status != MatchStatus.DONE && !predictionsLocked

    val borderColor = when (status) {
        MatchStatus.LIVE -> BorderLive
        else -> BorderColor
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF0D1321), Color(0xFF111827))))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        // Top shimmer for upcoming
        if (status == MatchStatus.UPCOMING) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, GoldGlow, Color.Transparent)))
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Round label + status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = roundLabel(evt.intRound),
                    color = GoldDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                StatusBadge(status = status, timeLocal = evt.strTimeLocal, timestamp = evt.strTimestamp, showDate = showDate)
            }

            // Teams row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamSection(
                    name = translateTeam(evt.strHomeTeam ?: "–"),
                    badgeUrl = getTeamFlagUrl(evt.strHomeTeam ?: ""),
                    isHome = true,
                    isDone = status == MatchStatus.DONE,
                    modifier = Modifier.weight(1f)
                )
                ScoreCenter(
                    homeScore = evt.intHomeScore,
                    awayScore = evt.intAwayScore,
                    status = status,
                    modifier = Modifier.width(72.dp)
                )
                TeamSection(
                    name = translateTeam(evt.strAwayTeam ?: "–"),
                    badgeUrl = getTeamFlagUrl(evt.strAwayTeam ?: ""),
                    isHome = false,
                    isDone = status == MatchStatus.DONE,
                    modifier = Modifier.weight(1f)
                )
            }

            // Venue
            if (!evt.strVenue.isNullOrBlank()) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = TextSecond,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = evt.strVenue,
                        color = TextSecond,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Prediction Section
            if (prediction != null || status != MatchStatus.DONE || predictionsLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderColor.copy(alpha = 0.5f))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canPredict && onPredictClick != null) {
                            onPredictClick?.invoke()
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (status == MatchStatus.DONE) {
                        if (prediction != null) {
                            val pts = calculatePredictionPoints(prediction, evt.intHomeScore, evt.intAwayScore)
                            Text(
                                text = "Tahmininiz: ${prediction.homeScore} – ${prediction.awayScore}",
                                color = TextSecond,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            val (badgeText, badgeBg, badgeFg) = when (pts) {
                                20 -> Triple("+20 Puan (Tam Skor)", Color(0x1F00E87A), GreenLive)
                                5  -> Triple("+5 Puan (Sonuç Doğru)", Color(0x1F3B82F6), BlueAccent)
                                else -> Triple("0 Puan", Color(0x1FFF4D6D), RedError)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(badgeBg)
                                    .border(1.dp, badgeFg.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(badgeText, color = badgeFg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "Tahmin yapılmadı",
                                color = TextSecond.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    } else if (predictionsLocked) {
                        if (prediction != null) {
                            Text(
                                text = "Tahmininiz: ${prediction.homeScore} – ${prediction.awayScore}",
                                color = TextSecond,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "Tahmin yapılmadı",
                                color = TextSecond.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = TextSecond,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = lockMessage ?: "Tahmin kilitli",
                                color = TextSecond,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End
                            )
                        }
                    } else {
                        // Upcoming match — tahmin açık
                        if (prediction != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = GreenLive,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Tahmininiz: ${prediction.homeScore} – ${prediction.awayScore}",
                                    color = Gold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = BlueAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Düzenle",
                                    color = BlueAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = TextSecond,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Tahmin yapılmadı",
                                    color = TextSecond,
                                    fontSize = 12.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(GoldGlow)
                                    .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Tahmin Et",
                                    color = Gold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (canPredict && onQuickPredict != null) {
                    QuickScorePicker(
                        selected = prediction,
                        onSelect = onQuickPredict
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickScorePicker(
    selected: MatchPrediction?,
    onSelect: (home: Int, away: Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Hızlı tahmin",
            color = TextSecond,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
        for (home in 0..3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (away in 0..3) {
                    val isSelected = selected?.homeScore == home && selected?.awayScore == away
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Gold.copy(alpha = 0.2f) else BgCard)
                            .border(
                                1.dp,
                                if (isSelected) Gold else BorderColor,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelect(home, away) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$home-$away",
                            color = if (isSelected) Gold else TextSecond,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamSection(
    name: String,
    badgeUrl: String?,
    isHome: Boolean,
    isDone: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (isHome) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TeamBadge(name = name, badgeUrl = badgeUrl)
        Text(
            text = name,
            color = if (isDone) TextSecond else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isHome) TextAlign.End else TextAlign.Start
        )
    }
}

@Composable
fun TeamBadge(name: String, badgeUrl: String?) {
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
                .padding(3.dp)
        )
    } else {
        // Initials fallback
        val initials = name.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(GoldDim, Gold))),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = BgDeep, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

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
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        } else {
            Text(
                text = "VS",
                color = TextSecond,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun StatusBadge(status: MatchStatus, timeLocal: String?, timestamp: String?, showDate: Boolean) {
    val trDt = convertToTurkeyDateTime(timestamp, null, timeLocal)
    val timeLabel = if (showDate && timestamp != null) {
        val dateText = formatShortDate(trDt.dateKey)
        "$dateText, ${trDt.timeStr}"
    } else {
        trDt.timeStr
    }
    val (text, bg, fg) = when (status) {
        MatchStatus.LIVE     -> Triple("● CANLI", Color(0x1F00E87A), GreenLive)
        MatchStatus.DONE     -> Triple("BİTTİ",   Color(0x0FFFFFFF), TextSecond)
        MatchStatus.UPCOMING -> Triple(timeLabel, Color(0x1F3B82F6), BlueAccent)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(40.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(40.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}
