package com.smsdetector.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smsdetector.app.ui.theme.*

@Composable
fun Avatar(
    initials: String,
    color: Int,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    online: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(color)),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value / 2.6).sp)
        }
        if (online) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Primary)
                    .border(2.dp, Bg, CircleShape)
            )
        }
    }
}

@Composable
fun BottomNav(current: String, onNavigate: (String) -> Unit) {
    NavigationBar(
        containerColor = Bg,
        tonalElevation = 0.dp,
        modifier = Modifier.height(72.dp)
    ) {
        NavigationBarItem(
            selected = current == "chats",
            onClick = { onNavigate("chats") },
            icon = { Icon(Icons.Filled.ChatBubbleOutline, null) },
            label = { Text("Chats") },
            colors = NavColors()
        )
        NavigationBarItem(
            selected = current == "scheduled",
            onClick = { onNavigate("scheduled") },
            icon = { Icon(Icons.Filled.ScheduleSend, null) },
            label = { Text("Scheduled") },
            colors = NavColors()
        )
        NavigationBarItem(
            selected = current == "security",
            onClick = { onNavigate("security") },
            icon = { Icon(Icons.Filled.VerifiedUser, null) },
            label = { Text("Security") },
            colors = NavColors()
        )
    }
}

@Composable
private fun NavColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Primary,
    selectedTextColor = TextPrimary,
    indicatorColor = Color(0xFF16354A),
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary
)

@Composable
fun VerifiedBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF153A38))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Verified, null, tint = VerifiedGreen, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text("Verified", color = VerifiedGreen, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        if (action != null) {
            Text(action, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onAction() })
        }
    }
}

@Composable
fun ScreenTopBar(title: String, subtitle: String? = null, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Primary, fontSize = 11.sp)
        }
        Row(content = actions)
    }
}
