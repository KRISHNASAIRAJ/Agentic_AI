package dev.tvsopen.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tvsopen.session.ConnectionState1
import dev.tvsopen.session.HudState
import dev.tvsopen.session.ParkedInfo
import dev.tvsopen.session.Telemetry
import dev.tvsopen.transport.ConnectionState
import dev.tvsopen.transport.label
import dev.tvsopen.ui.theme.AppSwitch
import dev.tvsopen.ui.theme.NumberStyle
import dev.tvsopen.ui.theme.PremiumColors

/**
 * Premium dashboard.
 *
 * Everything flows in one vertical column — no overlapping boxes — so each
 * card owns its own height and nothing can collide with the section above it.
 * Values that have not arrived from the cluster render as an em dash rather
 * than a zero, which would claim a reading the app never received.
 */
@Composable
fun HomeScreen(
    telemetry: Telemetry,
    hud: HudState,
    connection: ConnectionState1,
    parked: ParkedInfo?,
    findMyEnabled: Boolean,
    navMirroring: Boolean,
    rideMode: Boolean,
    onFindMyChange: (Boolean) -> Unit,
    onNavMirrorChange: (Boolean) -> Unit,
    onRideModeChange: (Boolean) -> Unit,
    onOpenOverview: () -> Unit,
    onOpenParked: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = connection.state == ConnectionState.CONNECTED
    val ctx = LocalContext.current

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Header(connection = connection, connected = connected)
        Spacer(Modifier.height(18.dp))

        HeroCard(telemetry = telemetry, connected = connected,
            address = connection.address, onConnect = onConnect,
            modifier = Modifier.enterAnimated(delayMs = 0))
        Spacer(Modifier.height(14.dp))

        if (hud.active) {
            NavigationStrip(hud = hud)
            Spacer(Modifier.height(14.dp))
        }

        SectionHeading("Ride statistics", trailing = if (connected) "LIVE" else null)
        Spacer(Modifier.height(10.dp))
        StatsRow(telemetry = telemetry, modifier = Modifier.enterAnimated(delayMs = 60))
        Spacer(Modifier.height(14.dp))

        SectionHeading("Fuel & range")
        Spacer(Modifier.height(10.dp))
        FuelCard(telemetry = telemetry, modifier = Modifier.enterAnimated(delayMs = 120))
        Spacer(Modifier.height(14.dp))

        SectionHeading("Quick actions")
        Spacer(Modifier.height(10.dp))
        QuickActions(
            navMirroring = navMirroring,
            onNavMirrorChange = onNavMirrorChange,
            onOpenOverview = onOpenOverview,
            onOpenParked = {
                if (parked == null || !openParkedLocation(ctx, parked)) onOpenParked()
            },
        )
        Spacer(Modifier.height(14.dp))

        SectionHeading("Scooter")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.DirectionsBike,
                label = "Find my scooter",
                checked = findMyEnabled,
                onCheckedChange = onFindMyChange,
            )
            SmallActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Bolt,
                label = "Ride mode",
                checked = rideMode,
                onCheckedChange = onRideModeChange,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Header(connection: ConnectionState1, connected: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Caption("GOOD RIDE, RIDER")
            Spacer(Modifier.height(4.dp))
            Text(
                "Jupiter 125",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                connection.model.displayName + " · " + connection.state.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(PremiumColors.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.TwoWheeler,
                contentDescription = null,
                tint = PremiumColors.Accent,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun HeroCard(
    telemetry: Telemetry,
    connected: Boolean,
    address: String?,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = PremiumColors.Card,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, PremiumColors.Hairline.copy(alpha = 0.7f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Caption("LIVE SPEEDOMETER")
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            telemetry.speedKmh?.toString() ?: "—",
                            style = NumberStyle,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "km/h",
                            style = MaterialTheme.typography.titleMedium,
                            color = PremiumColors.Accent,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                StatusPill(label = if (connected) "Connected" else "Disconnected", ok = connected)
            }

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                PremiumColors.Accent.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.TwoWheeler,
                    contentDescription = "Scooter",
                    tint = if (connected) PremiumColors.Accent else PremiumColors.Muted,
                    modifier = Modifier.size(88.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            if (!connected) {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PremiumColors.Accent,
                        contentColor = Color(0xFF0A0C07),
                    ),
                ) {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Find my cluster", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Caption("LINK ACTIVE", color = PremiumColors.Accent)
                    Spacer(Modifier.weight(1f))
                    Text(
                        address?.takeIf { it.isNotBlank() } ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationStrip(hud: HudState) {
    PremiumCard(color = PremiumColors.CardElevated) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Navigation, contentDescription = null,
                    tint = PremiumColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Caption("NAVIGATION MIRROR", color = PremiumColors.Accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                hud.instructionTitle ?: hud.street ?: "Following route",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (hud.distanceMeters >= 0) "${hud.distanceMeters} m" else "—",
                    style = NumberStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                hud.provider?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatsRow(telemetry: Telemetry, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.AutoMirrored.Filled.DirectionsBike,
            value = telemetry.connectedDistanceKm()?.let {
                String.format(java.util.Locale.US, "%.1f", it)
            } ?: "—",
            unit = "km",
            label = "Distance travelled",
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Speed,
            value = telemetry.topSpeedKmh?.toString() ?: "—",
            unit = "km/h",
            label = "Top speed",
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    unit: String,
    label: String,
) {
    PremiumCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = PremiumColors.Accent,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = NumberStyle, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(2.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.6.sp)
        }
    }
}

@Composable
private fun FuelCard(telemetry: Telemetry, modifier: Modifier = Modifier) {
    PremiumCard(modifier = modifier) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalGasStation, contentDescription = null,
                    tint = PremiumColors.Accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Caption("FUEL LEVEL")
                Spacer(Modifier.weight(1f))
                Text(
                    telemetry.fuelBars?.let { "$it / ${Telemetry.FUEL_BAR_MAX} bars" } ?: "—",
                    style = NumberStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FuelRing(bars = telemetry.fuelBars, maxBars = Telemetry.FUEL_BAR_MAX,
                    tankLitres = 5.2f)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigReadout(
                    modifier = Modifier.weight(1f),
                    value = telemetry.distanceToEmptyKm?.toString() ?: "—",
                    unit = "km",
                    caption = "Range left",
                    accent = true,
                )
                BigReadout(
                    modifier = Modifier.weight(1f),
                    value = telemetry.engineTempC?.toString() ?: "—",
                    unit = "°C",
                    caption = "Engine temp",
                )
            }
        }
    }
}

@Composable
private fun QuickActions(
    navMirroring: Boolean,
    onNavMirrorChange: (Boolean) -> Unit,
    onOpenOverview: () -> Unit,
    onOpenParked: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Navigation,
            label = if (navMirroring) "Nav on" else "Nav off",
            checked = navMirroring,
            onCheckedChange = onNavMirrorChange,
        )
        ActionTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Speed,
            label = "Overview",
            checked = false,
            onCheckedChange = { onOpenOverview() },
        )
        ActionTile(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Place,
            label = "Parked",
            checked = false,
            onCheckedChange = { onOpenParked() },
        )
    }
}

@Composable
private fun ActionTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = PremiumColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, PremiumColors.Hairline),
        onClick = { onCheckedChange(!checked) },
    ) {
        Column(
            Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = PremiumColors.Accent,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun SmallActionCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = PremiumColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, PremiumColors.Hairline),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = PremiumColors.Accent,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun openParkedLocation(ctx: android.content.Context, parked: ParkedInfo): Boolean {
    val label = parked.label ?: return false
    return try {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(label)}")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (e: Exception) {
        false
    }
}
