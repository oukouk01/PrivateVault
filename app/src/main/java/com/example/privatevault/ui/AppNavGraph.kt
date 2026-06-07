package com.example.privatevault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.privatevault.ui.lock.LockScreen
import com.example.privatevault.ui.lock.LockViewModel
import com.example.privatevault.ui.main.MainScreen
import com.example.privatevault.ui.main.MediaViewerScreen
import com.example.privatevault.ui.settings.SettingsScreen

object Routes {
    const val LOCK = "lock"
    const val MAIN = "main"
    const val VIEWER = "viewer/{type}/{file}"
    fun viewer(type: String, file: String) = "viewer/$type/${java.net.URLEncoder.encode(file, "UTF-8")}"
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    lockViewModel: LockViewModel
) {
    val startDestination = if (lockViewModel.isUnlocked) Routes.MAIN else Routes.LOCK

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOCK) {
            LockScreen(
                viewModel = lockViewModel,
                onUnlocked = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOCK) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
            MainScreen(
                onOpenViewer = { type, fileName ->
                    navController.navigate(Routes.viewer(type, fileName))
                },
                onLocked = {
                    lockViewModel.lock()
                    navController.navigate(Routes.LOCK) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.VIEWER) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "image"
            val file = backStackEntry.arguments?.getString("file")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: return@composable
            MediaViewerScreen(
                type = type,
                fileName = file,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
