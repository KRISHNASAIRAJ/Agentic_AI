package com.smsdetector.app.ui.scheduled

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsdetector.app.data.db.ScheduledMessage
import com.smsdetector.app.ui.components.BottomNav
import com.smsdetector.app.ui.theme.*

@Composable
fun ScheduledScreen(viewModel: ScheduledViewModel, onNavigate: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(containerColor = Bg, bottomBar = { BottomNav("scheduled", onNavigate) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Scheduled & Smart Tools", color = Primary, fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton({}) { Icon(Icons.Filled.Settings, null, tint = TextSecondary) }
                    IconButton({}) { Icon(Icons.Filled.MoreVert, null, tint = TextSecondary) }
                }
                Text("Automated queues & AI draft assistant", color = TextSecondary,
                    fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item { StatCards(state.pending.size) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Upcoming Scheduled Messages", color = TextPrimary, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${state.pending.size} queued", color = Primary, fontSize = 12.sp)
                }
            }
            items(state.pending, key = { it.id }) { item -> ScheduledCard(item, viewModel::cancel) }
            if (state.pending.isEmpty()) {
                item {
                    Text("Nothing scheduled yet. Use the button below to queue a message.",
                        color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
            }
            item {
                Text("✦  Smart Automation & AI Tools", color = TextPrimary, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
            }
            item { MagicDraftCard() }
            item { DrivingCard(state.drivingMode, viewModel::toggleDriving) }
            item {
                Button(onClick = { /* schedule dialog */ }, modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                    shape = RoundedCornerShape(28.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Schedule New Message", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatCards(count: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        StatCard(Modifier.weight(1f), "⏱", "$count", "Scheduled", "Upcoming messages ready for dispatch", "Queued", true)
        Spacer(Modifier.width(10.dp))
        StatCard(Modifier.weight(1f), "✦", "AI Smart", "Compose", "Gemini contextual autocpletions", "Active", false, purple = true)
    }
}

@Composable
private fun StatCard(
    modifier: Modifier, icon: String, big: String, title: String, desc: String,
    badge: String, active: Boolean, purple: Boolean = false
) {
    Card(modifier = modifier.height(170.dp), colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(if (purple) Color(0xFF2A2140) else Color(0xFF153A4D)),
                    contentAlignment = Alignment.Center) { Text(icon, fontSize = 16.sp) }
                StatusPill(badge, if (purple) Color(0xFF3A2A5A) else Color(0xFF153A4D), if (purple) Color(0xFFC4A8FF) else Primary)
            }
            Spacer(Modifier.height(14.dp))
            Text(big, color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
            Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(desc, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun StatusPill(text: String, bg: Color, fg: Color) {
    Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
private fun ScheduledCard(item: ScheduledMessage, onCancel: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF243B4A)), contentAlignment = Alignment.Center) {
                    Text(item.displayName.take(2).uppercase(), color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarToday, null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(when {
                            item.isRecurring -> item.recurringLabel ?: "Recurring"
                            else -> java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                                .format(java.util.Date(item.scheduledAt))
                        }, color = TextSecondary, fontSize = 11.sp)
                    }
                }
                StatusPill("Pending", Color(0xFF153A4D), Primary)
                IconButton(onClick = {}) { Icon(Icons.Filled.Edit, null, tint = TextSecondary, modifier = Modifier.size(18.dp)) }
                IconButton(onClick = { onCancel(item.id) }) { Icon(Icons.Filled.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            Text("\"${item.body}\"", color = TextSecondary, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(SurfaceHigh, RoundedCornerShape(12.dp)).padding(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MagicDraftCard() {
    var prompt by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦  Magic Draft Assistant", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                StatusPill("Powered by Gemini", Color(0xFF2A2140), Color(0xFFC4A8FF))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = prompt, onValueChange = { prompt = it },
                placeholder = { Text("Draft a polite delay notice...", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceHigh, unfocusedContainerColor = SurfaceHigh,
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Primary))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("+ Reschedule meeting", "+ Running late").forEach {
                    SuggestionChip(onClick = { prompt = it }, label = { Text(it, fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = SurfaceHigh, labelColor = TextSecondary),
                        modifier = Modifier.padding(end = 6.dp))
                }
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                    shape = RoundedCornerShape(18.dp)) { Text("Generate with Gemini", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun DrivingCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(SurfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DirectionsCar, null, tint = Primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Driving Mode Auto-reply", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Sends an automated response when Android Auto connects.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("\"I'm currently driving and will reply once parked.\"",
                    color = TextSecondary, fontSize = 11.sp,
                    modifier = Modifier.background(SurfaceHigh, RoundedCornerShape(10.dp)).padding(8.dp))
            }
            Switch(checked = enabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF6D5EF6),
                    uncheckedTrackColor = Outline))
        }
    }
}
