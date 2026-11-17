package dev.tvsopen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvsopen.session.Telemetry
import dev.tvsopen.ui.theme.NumberStyle
import dev.tvsopen.ui.theme.PremiumColors

/**
 * Stats / Vehicle Overview.
 *
 * Premium dark restyle of the cluster telemetry screen. The headline figures
 * lead, the animated fuel ring replaces the old segmented bar, and the rest of
 * the decoded fields follow in a calm two-column grid. Everything is a single
 * vertical column so cards cannot overlap.
 */
@Composable
fun OverviewScreen(
    telemetry: Telemetry,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Caption("VEHICLE OVERVIEW")
        Spacer(Modifier.height(4.dp))
        Text("Jupiter 125", style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("Live from the instrument cluster", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.enterAnimated(delayMs = 0)) {
            BigStat(Modifier.weight(1f), Icons.Filled.Speed,
                telemetry.topSpeedKmh?.toString() ?: "—", "km/h", "Top speed")
            BigStat(Modifier.weight(1f), Icons.Filled.Bolt,
                telemetry.engineRpm?.toString() ?: "—", "rpm", "Engine RPM")
        }
        Spacer(Modifier.height(12.dp))
        BigStat(Modifier.fillMaxWidth().enterAnimated(delayMs = 40),
            Icons.Filled.Timer,
            telemetry.timeTo60Sec?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "—",
            "sec", "Best 0–60")

        Spacer(Modifier.height(18.dp))
        SectionHeading("Fuel", trailing = "5.2 L TANK")
        Spacer(Modifier.height(10.dp))
        PremiumCard(modifier = Modifier.fillMaxWidth().enterAnimated(delayMs = 80)) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                FuelRing(bars = telemetry.fuelBars, maxBars = Telemetry.FUEL_BAR_MAX,
                    tankLitres = 5.2f)
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeading("Distance travelled")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DataTile(Modifier.weight(1f), Icons.Filled.LocalGasStation,
                telemetry.odometerKm?.let { String.format(java.util.Locale.US, "%.1f", it) }
                    ?: "—", "Total km")
            DataTile(Modifier.weight(1f), Icons.Filled.LocalGasStation,
                telemetry.connectedDistanceKm()?.let {
                    String.format(java.util.Locale.US, "%.1f", it)
                } ?: "—",
                "This link (km)")
        }

        Spacer(Modifier.height(18.dp))
        SectionHeading("Cluster telemetry")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DataTile(Modifier.weight(1f), Icons.Filled.BatteryChargingFull,
                telemetry.batteryVoltage?.let { "%.1f".format(it / 10.0) } ?: "—", "Battery (V)")
            DataTile(Modifier.weight(1f), Icons.Filled.Thermostat,
                telemetry.engineTempC?.toString() ?: "—", "Engine (°C)")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DataTile(Modifier.weight(1f), Icons.Filled.LocalGasStation,
                telemetry.distanceToEmptyKm?.toString() ?: "—", "Range (km)")
            DataTile(Modifier.weight(1f), Icons.Filled.Build,
                when (telemetry.serviceDue) { true -> "Due"; false -> "OK"; null -> "—" },
                "Service")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DataTile(Modifier.weight(1f), Icons.Filled.Warning,
                when (telemetry.milOn) {
                    true -> "On"; false -> "Off"; null -> "—"
                }, "Check engine")
            DataTile(Modifier.weight(1f), Icons.Filled.Speed,
                telemetry.avgEconomyKmpl?.let { "$it kmpl" } ?: "—", "Avg economy")
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun BigStat(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    unit: String,
    label: String,
) {
    PremiumCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = PremiumColors.Accent,
                modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = NumberStyle, fontSize = 30.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.size(4.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp))
            }
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DataTile(modifier: Modifier, icon: ImageVector, value: String, label: String) {
    PremiumCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = PremiumColors.Accent,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, style = NumberStyle, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
