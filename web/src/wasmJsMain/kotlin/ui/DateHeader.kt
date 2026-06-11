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

@Composable
fun SectionHeader(title: String, matchCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(40.dp))
                .background(Brush.horizontalGradient(listOf(GoldDim, Gold)))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = title,
                color = BgDeep,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp
            )
        }

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = BorderColor,
            thickness = 1.dp
        )

        Text(
            text = "$matchCount maç",
            color = TextSecond,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DateHeader(dateKey: String, matchCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(40.dp))
                .background(Brush.horizontalGradient(listOf(GoldDim, Gold)))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = formatDateKey(dateKey),
                color = BgDeep,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp
            )
        }

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = BorderColor,
            thickness = 1.dp
        )

        Text(
            text = "$matchCount maç",
            color = TextSecond,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
