package com.abgmie.vpn

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abgmie.vpn.core.AbgMieTheme
import com.abgmie.vpn.core.DeviceType
import com.abgmie.vpn.domain.ThemeMode
import com.abgmie.vpn.mobile.MobileApp
import com.abgmie.vpn.tv.TvApp
import com.abgmie.vpn.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isTv = DeviceType.isTv(this)
        // Phones: portrait. Tablets: portrait + landscape. TV: landscape 16:9.
        requestedOrientation = when {
            isTv -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            resources.configuration.smallestScreenWidthDp < 600 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        setContent {
            val vm: MainViewModel = hiltViewModel()
            val s by vm.settings.collectAsStateWithLifecycle()
            val dark = when (s.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme() || isTv
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            AbgMieTheme(dark) { AppRoot(vm, isTv) }
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel, isTv: Boolean) {
    val snackbar = remember { SnackbarHostState() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onPermissionResult(it.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(Unit) { vm.permissionRequests.collect { permission.launch(it) } }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.externalLaunches.collect { runCatching { ctx.startActivity(it) } } }
    if (isTv) TvApp(vm, snackbar) else MobileApp(vm, snackbar)
}
