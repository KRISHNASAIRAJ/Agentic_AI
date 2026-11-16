package com.smsdetector.app.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsdetector.app.data.db.Category
import com.smsdetector.app.ui.components.BottomNav
import com.smsdetector.app.ui.components.SectionHeader
import com.smsdetector.app.ui.theme.*

@Composable
fun SecurityScreen(
    viewModel: SecurityViewModel,
    onNavigate: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(containerColor = Bg, bottomBar = { BottomNav("security", onNavigate) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Security & Spam Protection", color = TextPrimary, fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton({}) { Icon(Icons.Filled.Info, null, tint = Primary) }
                    IconButton({}) { Icon(Icons.Filled.MoreVert, null, tint = TextPrimary) }
                }
                Text("• RCS Encryption & Safety Controls", color = Primary,
                    fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item { ProtectionCard(state.spamCount, state.ruleVersion) }
            item { SectionHeader("PRIVACY SAFEGUARDS", "All Engines Operational") }
            item {
                ToggleCard(Icons.Filled.DeleteSweep, "Auto-Delete OTPs & Passcodes",
                    "Automatically delete one-time codes and 2FA SMS after 24 hours to keep inbox clean and secure.",
                    state.autoDeleteOtp, viewModel::toggleOtp)
            }
            item {
                ToggleCard(Icons.Filled.Shield, "Real-time AI Spam & Scam Protection",
                    "Identify and filter phishing, suspicious links, and unverified senders.",
                    state.aiSpam, viewModel::toggleAi, "Protected")
            }
            item {
                SettingCard(Icons.Filled.Verified, "Verified Business Sender Badges",
                    "Only display verified logos and trust seals for authenticated corporate senders (Banks, Airlines).",
                    state.verifiedBadges) { viewModel.toggleBadges(!state.verifiedBadges) }
            }
            item {
                SettingCard(Icons.Filled.Key, "End-to-End Encryption (E2EE) Keys",
                    "Verify identity keys and active RCS protocol sessions.", state.verifiedBadges, showArrow = true) {}
            }
            item {
                SectionHeader("QUARANTINE & BLOCKLIST", "Manage All (${state.blocked.size})")
            }
            items(state.blocked, key = { it.address }) { item ->
                BlockedSenderCard(item, viewModel::unblock)
            }
            if (state.blocked.isEmpty()) {
                item {
                    Text("No blocked senders yet. Detected spam will appear here.",
                        color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun ProtectionCard(spamCount: Int, ruleVersion: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF152A3A), Color(0xFF131C26)))
            ).padding(20.dp)
        ) {
            Column {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B3E54)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.VerifiedUser, null, tint = Primary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Device Protected", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    StatusBadge("Active")
                }
                Spacer(Modifier.height(6.dp))
                Text("AI Spam filter blocked $spamCount suspicious messages this month. RCS end-to-end encryption active.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(16.dp))
                Row {
                    InfoChip("Signature Database", "Updated Today, 04:12")
                    Spacer(Modifier.width(10.dp))
                    InfoChip("Cipher Suite", "Signal Protocol (E2EE)")
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(Modifier.background(SurfaceHigh, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label, color = TextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusBadge(text: String) {
    Text(text, color = Primary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(Color(0xFF153A4D), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit, badge: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(SurfaceVariant), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (badge != null) { Spacer(Modifier.width(6.dp)); StatusBadge(badge) }
                }
                Spacer(Modifier.height(4.dp))
                Text(desc, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF6D5EF6),
                    uncheckedTrackColor = Outline))
        }
    }
}

@Composable
private fun SettingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, desc: String, enabled: Boolean, showArrow: Boolean = false, onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(16.dp).then(if (!showArrow) Modifier else Modifier), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(SurfaceVariant), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(desc, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (showArrow) Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary)
            else Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF153A4D), contentColor = Primary),
                shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
                Text(if (enabled) "Enabled" else "Disabled", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BlockedSenderCard(
    sender: com.smsdetector.app.data.db.BlockedSender,
    onUnblock: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Block, null, tint = SpamRed, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(sender.address, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WarningAmber, null, tint = SpamRed, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(sender.reason, color = SpamRed, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text("Intercepted ${timeAgo(sender.interceptedAt)}", color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onUnblock(sender.address) }) { Text("Unblock", color = TextSecondary, fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF153A4D), contentColor = Primary),
                    shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                    Text("Report", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun timeAgo(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} hours ago"
        else -> "yesterday, ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))}"
    }
}
