package com.smsdetector.app.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsdetector.app.data.db.Category
import com.smsdetector.app.data.db.Conversation
import com.smsdetector.app.ui.components.Avatar
import com.smsdetector.app.ui.components.BottomNav
import com.smsdetector.app.ui.components.VerifiedBadge
import com.smsdetector.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    onOpenThread: (String) -> Unit,
    onOpenSecurity: () -> Unit,
    onRequestDefaultSms: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    Scaffold(
        containerColor = Bg,
        bottomBar = { BottomNav("chats", onNavigate) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { /* new message */ },
                containerColor = Primary,
                contentColor = OnPrimary,
                icon = { Icon(Icons.Filled.Edit, null) },
                text = { Text("Start chat", fontWeight = FontWeight.SemiBold) },
                shape = RoundedCornerShape(28.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item { SearchHeader(state.query, viewModel::setQuery) }
            item { FilterChips(state.filter, viewModel::setFilter) }

            if (state.pinned.isNotEmpty()) {
                item { PinnedSection(state.pinned, onOpenThread) }
            }

            if (!state.isDefaultSms) {
                item { DefaultSmsBanner(onRequestDefaultSms) }
            }

            items(state.conversations, key = { it.address }) { conv ->
                ConversationRow(conv, onOpenThread, viewModel::togglePin) { viewModel.markRead(conv.address) }
            }
        }
    }
}

@Composable
private fun SearchHeader(query: String, onQuery: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            modifier = Modifier.weight(1f).height(52.dp)
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ChatBubbleOutline, null, tint = Primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.TextField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = { Text("Search messages & contacts", color = TextSecondary, fontSize = 14.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Primary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.Mic, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Avatar("SK", 0xFF3A8DFF.toInt(), 40.dp, online = true)
        Icon(Icons.Filled.MoreVert, null, tint = TextPrimary)
    }
}

@Composable
private fun FilterChips(current: InboxFilter, onSelect: (InboxFilter) -> Unit) {
    val chips = listOf(
        "All" to InboxFilter.ALL, "Personal" to InboxFilter.PERSONAL,
        "OTPs & Banks" to InboxFilter.OTP, "Promotions" to InboxFilter.PROMOTION
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        chips.forEach { (label, filter) ->
            val selected = current == filter
            FilterChip(
                selected = selected,
                onClick = { onSelect(filter) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF123C52),
                    selectedLabelColor = Primary,
                    containerColor = Surface,
                    labelColor = TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = selected,
                    selectedBorderColor = Primary.copy(alpha = 0.5f),
                    borderColor = Outline
                ),
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun PinnedSection(pinned: List<Conversation>, onOpen: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Top
        ) {
            pinned.take(4).forEach { conv ->
                Column(
                    modifier = Modifier.width(96.dp).padding(end = 4.dp).clip(RoundedCornerShape(16.dp))
                        .background(SurfaceHigh).clickable { onOpen(conv.address) }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(conv.initials, conv.avatarColor, 56.dp, online = conv.isRcs)
                    Spacer(Modifier.height(10.dp))
                    Text(conv.displayName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (conv.unreadCount > 0) "${conv.unreadCount} new" else if (conv.isRcs) "RCS Chat" else "Offline",
                        color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DefaultSmsBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A36)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PriorityHigh, null, tint = Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Set as default SMS app", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Required to send & receive messages", color = TextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Primary)
        }
    }
}

@Composable
private fun ConversationRow(
    conv: Conversation,
    onOpen: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onRead: () -> Unit
) {
    val isSpam = conv.category == Category.SPAM || conv.isBlocked
    val previewColor = if (isSpam) SpamRed else TextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSpam) Color(0xFF2A1517) else Surface)
            .clickable { onRead(); onOpen(conv.address) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(conv.initials, conv.avatarColor, 52.dp, online = conv.isRcs)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(conv.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (conv.isVerified) { Spacer(Modifier.width(6.dp)); VerifiedBadge() }
            }
            Spacer(Modifier.height(3.dp))
            Text(conv.lastMessage, color = previewColor, fontSize = 13.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conv.isRcs) {
                    Icon(Icons.Filled.Lock, null, tint = VerifiedGreen, modifier = Modifier.size(12.dp))
                    Text("RCS Chat • Encrypted", color = VerifiedGreen, fontSize = 11.sp)
                } else {
                    Text(when (conv.category) {
                        Category.OTP -> "OTP code"
                        Category.BANK -> "Bank alert"
                        Category.PROMOTION -> "Promotion"
                        Category.SPAM -> "Suspected spam"
                        else -> "SMS"
                    }, color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (conv.lastMessageTs == 0L) "" else formatTime(conv.lastMessageTs),
                color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            if (conv.unreadCount > 0) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                    Text(conv.unreadCount.toString(), color = OnPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                IconButton(onClick = { onTogglePin(conv.address, !conv.isPinned) }, modifier = Modifier.size(24.dp)) {
                    Icon(if (conv.isPinned) Icons.Filled.PushPin else Icons.Filled.MoreVert, null,
                        tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun formatTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 172_800_000 -> "Yesterday"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }
}
