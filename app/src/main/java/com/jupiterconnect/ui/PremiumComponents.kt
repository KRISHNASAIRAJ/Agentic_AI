package com.jupiterconnect.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiterconnect.ui.theme.JupiterColors
import com.jupiterconnect.ui.theme.NumberStyle

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

@Composable
fun StatusPill(label: String, ok: Boolean) {
    val accent = JupiterColors.Accent
    val pulse = if (ok) rememberPulseAlpha() else null
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (ok) accent.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        border = BorderStroke(
            1.dp,
            if (ok) accent.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                color = if (accent) JupiterColors.Accent else MaterialTheme.colorScheme.onSurface,
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
        Text(
            caption.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
fun AccentStrip() {
    Box(
        Modifier.fillMaxWidth().height(2.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, JupiterColors.Accent, Color.Transparent)),
        ),
    )
}

@Composable
fun SectionHeading(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Caption(title)
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = JupiterColors.Accent)
        }
    }
}

/**
 * Animated 270° fuel gauge for the Jupiter 125's 5.2 L tank.
 * The cluster reports 0–6 bars; each bar is ~0.87 L. Turns amber below 20%.
 */
@Composable
fun FuelRing(
    bars: Int,
    maxBars: Int = 6,
    tankLitres: Float = 5.2f,
    modifier: Modifier = Modifier,
) {
    val fraction = bars.coerceIn(0, maxBars) / maxBars.toFloat()
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "fuel-ring",
    )
    val pct = (fraction * 100).toInt()
    val litres = fraction * tankLitres
    val ringColor = if (fraction <= 0.2f) JupiterColors.Amber else JupiterColors.Accent
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)

    Box(modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(168.dp)) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor, startAngle = 135f, sweepAngle = 270f,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = ringColor, startAngle = 135f, sweepAngle = 270f * animated,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$pct%", style = NumberStyle, fontSize = 34.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
            )
            Text("%.1f L".format(litres), style = MaterialTheme.typography.labelSmall, color = ringColor)
            Spacer(Modifier.height(2.dp))
            Text("OF ${tankLitres.toInt()} L", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = JupiterColors.Accent,
            checkedThumbColor = JupiterColors.OnPrimary,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = MaterialTheme.colorScheme.outline,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}
