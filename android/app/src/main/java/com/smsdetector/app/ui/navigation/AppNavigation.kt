package com.smsdetector.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smsdetector.app.SmsDetectorApplication
import com.smsdetector.app.ui.inbox.InboxScreen
import com.smsdetector.app.ui.inbox.InboxViewModel
import com.smsdetector.app.ui.scheduled.ScheduledScreen
import com.smsdetector.app.ui.scheduled.ScheduledViewModel
import com.smsdetector.app.ui.security.SecurityScreen
import com.smsdetector.app.ui.security.SecurityViewModel
import com.smsdetector.app.ui.thread.ThreadScreen
import com.smsdetector.app.ui.thread.ThreadViewModel

@Composable
fun AppNavigation(
    onRequestDefaultSms: () -> Unit,
    isDefaultSms: () -> Boolean
) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as SmsDetectorApplication

    NavHost(navController = nav, startDestination = "chats") {
        composable("chats") {
            val vm: InboxViewModel = viewModel(factory = InboxViewModel.factory(app, isDefaultSms))
            InboxScreen(
                viewModel = vm,
                onOpenThread = { address -> nav.navigate("thread/${android.net.Uri.encode(address)}") },
                onOpenSecurity = { nav.navigate("security") },
                onRequestDefaultSms = onRequestDefaultSms,
                onNavigate = { route -> nav.navigate(route) }
            )
        }
        composable(
            "thread/{address}",
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) { entry ->
            val address = android.net.Uri.decode(entry.arguments?.getString("address") ?: "")
            val vm: ThreadViewModel = viewModel(factory = ThreadViewModel.factory(app, address))
            ThreadScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("scheduled") {
            val vm: ScheduledViewModel = viewModel(factory = ScheduledViewModel.factory(app))
            ScheduledScreen(vm, onNavigate = { route -> nav.navigate(route) })
        }
        composable("security") {
            val vm: SecurityViewModel = viewModel(factory = SecurityViewModel.factory(app))
            SecurityScreen(vm, onNavigate = { route -> nav.navigate(route) })
        }
    }
}
