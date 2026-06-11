@file:OptIn(kotlin.time.ExperimentalTime::class)

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant

// Açılış maçı: Meksika – Güney Afrika (11 Haziran 2026, 22:00 TR / 19:00 UTC)
private val worldCupKickoff = Instant.parse("2026-06-11T19:00:00Z")

data class CountdownState(
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val isStarted: Boolean
)

fun calculateWorldCupCountdown(now: Instant = Clock.System.now()): CountdownState {
    if (now >= worldCupKickoff) {
        return CountdownState(0, 0, 0, 0, isStarted = true)
    }

    val remainingSeconds = (worldCupKickoff - now).inWholeSeconds
    return CountdownState(
        days = (remainingSeconds / 86_400).toInt(),
        hours = ((remainingSeconds % 86_400) / 3_600).toInt(),
        minutes = ((remainingSeconds % 3_600) / 60).toInt(),
        seconds = (remainingSeconds % 60).toInt(),
        isStarted = false
    )
}

@Composable
fun WorldCupCountdown(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }

    val countdown = remember(tick) { calculateWorldCupCountdown() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        if (countdown.isStarted) {
            Text(
                text = "Dünya Kupası başladı!",
                color = GreenLive,
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "Başlamasına",
                color = TextSecond,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountdownUnit(value = countdown.days, label = "gün", compact = compact)
                CountdownUnit(value = countdown.hours, label = "saat", compact = compact)
                CountdownUnit(value = countdown.minutes, label = "dk", compact = compact)
                CountdownUnit(value = countdown.seconds, label = "sn", compact = compact)
            }

            if (!compact) {
                Text(
                    text = "11 Haziran 2026 · 22:00 (TR)",
                    color = TextSecond.copy(alpha = 0.75f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun CountdownUnit(value: Int, label: String, compact: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = if (compact) 36.dp else 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BgCard)
                .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 4.dp else 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value.toString(),
                color = Gold,
                fontSize = if (compact) 14.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = label,
            color = TextSecond,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
