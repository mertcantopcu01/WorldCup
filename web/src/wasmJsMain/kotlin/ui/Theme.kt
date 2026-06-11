import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Color Palette ─────────────────────────────────────────────────────────────
val Gold        = Color(0xFFF5C942)
val GoldDim     = Color(0xFFC8983A)
val GoldGlow    = Color(0x33F5C942)
val BgDeep      = Color(0xFF04080F)
val BgCard      = Color(0xFF0D1321)
val BgGlass     = Color(0x0AFFFFFF)
val TextPrimary = Color(0xFFF0F4FF)
val TextSecond  = Color(0xFF8B95AA)
val GreenLive   = Color(0xFF00E87A)
val RedError    = Color(0xFFFF4D6D)
val BlueAccent  = Color(0xFF3B82F6)
val BorderColor = Color(0x12FFFFFF)
val BorderLive  = Color(0x5A00E87A)

// ── Theme ─────────────────────────────────────────────────────────────────────
private val WorldCupColors = darkColorScheme(
    primary          = Gold,
    onPrimary        = BgDeep,
    primaryContainer = GoldGlow,
    background       = BgDeep,
    surface          = BgCard,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    outline          = BorderColor,
    error            = RedError
)

@Composable
fun WorldCupTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WorldCupColors, content = content)
}
