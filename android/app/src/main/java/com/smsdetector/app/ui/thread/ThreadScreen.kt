package com.smsdetector.app.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.smsdetector.app.data.db.Direction
import com.smsdetector.app.data.db.Message
import com.smsdetector.app.data.db.MessageType
import com.smsdetector.app.ui.components.Avatar
import com.smsdetector.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(viewModel: ThreadViewModel, onBack: () -> Unit) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    Scaffold(containerColor = Bg, topBar = {
        Column {
            ThreadTopBar(title, onBack)
            HorizontalDivider(color = Outline, thickness = 0.5.dp)
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)
            ) {
                item { DateChip() }
                item { EncryptionNotice() }
                items(messages, key = { it.id }) { msg -> MessageBubble(msg, viewModel::delete) }
            }
            QuickReplies { draft = it }
            Composer(draft, { draft = it }, { viewModel.send(draft); draft = "" })
        }
    }
}

@Composable
private fun ThreadTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(66.dp).background(Surface).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
        Avatar(title.take(2).uppercase(), 0xFF3A8DFF.toInt(), 40.dp, online = true)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Icon(Icons.Filled.Verified, null, tint = Primary, modifier = Modifier.size(16.dp).padding(start = 4.dp))
            }
            Text("RCS Chat  •  End-to-end encrypted", color = Primary, fontSize = 11.sp)
        }
        IconButton({}) { Icon(Icons.Filled.Videocam, null, tint = TextPrimary) }
        IconButton({}) { Icon(Icons.Filled.Phone, null, tint = TextPrimary) }
        IconButton({}) { Icon(Icons.Filled.MoreVert, null, tint = TextPrimary) }
    }
}

@Composable
private fun DateChip() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text("TODAY, OCTOBER 24", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.background(SurfaceHigh, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
private fun EncryptionNotice() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
            .background(Surface, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Lock, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("RCS chat is end-to-end encrypted. Your private keys never leave this device.",
            color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun MessageBubble(msg: Message, onDelete: (Long) -> Unit) {
    val outgoing = msg.direction == Direction.OUTGOING
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        when (msg.type) {
            MessageType.VOICE -> VoiceBubble(msg, outgoing)
            MessageType.IMAGE -> ImageBubble(msg, outgoing)
            MessageType.OTP -> TextBubble(msg, outgoing, otp = true, onDelete = onDelete)
            else -> TextBubble(msg, outgoing, otp = false, onDelete = onDelete)
        }
    }
}

@Composable
private fun TextBubble(msg: Message, outgoing: Boolean, otp: Boolean, onDelete: (Long) -> Unit) {
    val bg = if (outgoing) Primary else SurfaceHigh
    val fg = if (outgoing) OnPrimary else TextPrimary
    Column(
        modifier = Modifier.widthIn(max = 300.dp).background(bg, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (otp && msg.otpCode != null) {
            Text("OTP: ${msg.otpCode}", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Text("Expires soon", color = fg.copy(alpha = 0.7f), fontSize = 11.sp)
        } else {
            Text(msg.body, color = fg, fontSize = 15.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Text(timeOf(msg.timestamp), color = fg.copy(alpha = 0.65f), fontSize = 10.sp)
            if (outgoing) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.DoneAll, null, tint = fg.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun VoiceBubble(msg: Message, outgoing: Boolean) {
    Row(
        modifier = Modifier.widthIn(max = 300.dp).background(if (outgoing) Primary else SurfaceHigh, RoundedCornerShape(18.dp))
            .padding(10.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (outgoing) OnPrimary.copy(alpha = 0.15f) else Primary),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, null, tint = if (outgoing) Primary else OnPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bars = listOf(8, 16, 24, 14, 28, 12, 20, 10, 18, 8, 14, 6)
                bars.forEach { h ->
                    Box(Modifier.width(3.dp).height(h.dp).background(
                        if (outgoing) OnPrimary.copy(alpha = 0.5f) else Primary, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(2.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0:14", color = TextSecondary, fontSize = 10.sp)
                Text(timeOf(msg.timestamp), color = TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ImageBubble(msg: Message, outgoing: Boolean) {
    Column(modifier = Modifier.widthIn(max = 280.dp).background(SurfaceHigh, RoundedCornerShape(18.dp)).padding(8.dp)) {
        Box(Modifier.height(160.dp).fillMaxWidth().background(Color(0xFF2A3038), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Image, null, tint = TextSecondary, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("4.2 MB  •  RAW", color = TextSecondary, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickReplies(onPick: (String) -> Unit) {
    val replies = listOf("Looks amazing! 🎉", "On my way", "🔗 Send prototype")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
        replies.forEach {
            SuggestionChip(onClick = { onPick(it) }, label = { Text(it, fontSize = 12.sp) },
                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = SurfaceHigh, labelColor = TextPrimary),
                modifier = Modifier.padding(end = 6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton({}) { Icon(Icons.Filled.EmojiEmotions, null, tint = TextSecondary) }
        OutlinedTextField(
            value = draft, onValueChange = onDraft,
            placeholder = { Text("RCS message", color = TextSecondary) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceHigh, unfocusedContainerColor = SurfaceHigh,
                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Primary
            )
        )
        IconButton({}) { Icon(Icons.Filled.AttachFile, null, tint = TextSecondary) }
        IconButton({}) { Icon(Icons.Filled.Mic, null, tint = TextSecondary) }
        FloatingActionButton(onClick = onSend, containerColor = Primary, contentColor = OnPrimary,
            modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Send, null)
        }
    }
}

private fun timeOf(ts: Long): String =
    java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ts))
