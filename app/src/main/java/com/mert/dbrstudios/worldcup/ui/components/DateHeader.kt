package com.mert.dbrstudios.worldcup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mert.dbrstudios.worldcup.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DateHeader(dateKey: String, matchCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Gold pill with formatted date
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(40.dp))
                .background(
                    Brush.horizontalGradient(listOf(GoldDim, Gold))
                )
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = formatDate(dateKey),
                color = BgDeep,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp
            )
        }

        // Divider
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = BorderColor,
            thickness = 1.dp
        )

        // Count chip
        Text(
            text = "$matchCount maç",
            color = TextSecond,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDate(dateKey: String): String {
    return try {
        val date = LocalDate.parse(dateKey, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.forLanguageTag("tr-TR"))
        date.format(formatter).replaceFirstChar { it.uppercase() }
    } catch (_: Exception) {
        dateKey
    }
}
