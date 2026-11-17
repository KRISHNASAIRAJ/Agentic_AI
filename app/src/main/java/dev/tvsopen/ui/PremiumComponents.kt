package dev.tvsopen.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvsopen.ui.theme.NumberStyle
import dev.tvsopen.ui.theme.PremiumColors

/** Small uppercase caption used above sections and on card eyebrows. */
@Composable
fun Caption(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = color,
    )
}

/** Elevated premium card with a hairline border. */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(content = content)
    }
}

/** Pill status badge: filled accent when ok, muted outline otherwise. */
@Composable
fun StatusPill(label: String, ok: Boolean) {
    val accent = PremiumColors.Accent
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (ok) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        border = BorderStroke(
            1.dp,
            if (ok) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pulse = if (ok) rememberPulseAlpha() else null
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer { alpha = pulse?.invoke() ?: 1f }
                    .clip(RoundedCornerShape(50))
                    .background(if (ok) accent else MaterialTheme.colorScheme.onSurfaceVariant),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = if (ok) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A single big telemetry number with unit and caption. */
@Composable
fun BigReadout(
    value: String,
    unit: String,
    caption: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = NumberStyle,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = if (accent) PremiumColors.Accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(caption.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp)
    }
}

/** Horizontal segmented bar, used for the fuel gauge. */
@Composable
fun SegmentBar(fraction: Float, segments: Int = 6) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(segments) { i ->
            val filled = i.toFloat() < fraction * segments
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (filled) PremiumColors.Accent
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    ),
            )
        }
    }
}

/** Decorative gradient strip used on the hero card's lower edge. */
@Composable
fun AccentStrip() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, PremiumColors.Accent, Color.Transparent),
                ),
            ),
    )
}

/** Section heading with an optional trailing action label. */
@Composable
fun SectionHeading(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Caption(title)
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelSmall,
                color = PremiumColors.Accent)
        }
    }
}

/**
 * Animated circular fuel gauge representing the Jupiter 125's 5.2 L tank.
 *
 * The cluster reports fuel as 0–6 bars; each bar is therefore ~0.87 L. We
 * convert to a percentage and litres, then sweep a 270° ring with a smooth
 * animation so a changing level is obvious at a glance. The arc is clamped to
 * 0..1 and turns amber below 20% as a low-fuel cue.
 */
@Composable
fun FuelRing(
    bars: Int?,
    maxBars: Int = 6,
    tankLitres: Float = 5.2f,
    modifier: Modifier = Modifier,
) {
    val fraction = ((bars ?: 0).coerceIn(0, maxBars)) / maxBars.toFloat()
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "fuel-ring",
    )
    val pct = (fraction * 100).toInt()
    val litres = fraction * tankLitres
    val ringColor = if (bars != null && fraction <= 0.2f) PremiumColors.Amber else PremiumColors.Accent
    // Hoist theme colours out of the Canvas draw scope (not a composable context).
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)

    Box(modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(168.dp)) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val startAngle = 135f
            val sweep = 270f
            // Track.
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Value.
            if (animated > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = startAngle,
                    sweepAngle = sweep * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (bars != null) "$pct%" else "—",
                style = NumberStyle,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (bars != null) "%.1f L".format(litres) else "no reading",
                style = MaterialTheme.typography.labelSmall,
                color = ringColor,
            )
            Spacer(Modifier.height(2.dp))
            Text("OF ${tankLitres.toInt()} L", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Centered empty-state note. */
@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}
