package com.opentonex.controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import java.io.File
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.ui.connect.ConnectScreen
import com.opentonex.controller.ui.editor.EditorScreen
import com.opentonex.controller.ui.home.HomeScreen
import com.opentonex.controller.ui.settings.SettingsScreen

private enum class TopLevelDestination(val route: String, val label: String) {
    HOME("home", "Presets"),
    EDITOR("editor", "Editor"),
    SETTINGS("settings", "Config")
}

private fun TopLevelDestination.icon() = when (this) {
    TopLevelDestination.HOME -> Icons.Filled.ViewList
    TopLevelDestination.EDITOR -> Icons.Filled.Tune
    TopLevelDestination.SETTINGS -> Icons.Filled.Settings
}

@Composable
fun ToneXApp(
    windowSizeClass: WindowSizeClass,
    onCreateRealConnection: suspend () -> PedalConnection?,
    onCreateFakeConnection: () -> PedalConnection,
    onResolveCaptureDirectory: () -> File,
    viewModel: PedalViewModel = viewModel()
) {
    val connectionState by viewModel.state.collectAsStateWithLifecycle()
    val errorMessage by viewModel.error.collectAsStateWithLifecycle()
    val captureState by viewModel.capture.collectAsStateWithLifecycle()
    val busyState by viewModel.busy.collectAsStateWithLifecycle()

    when (val current = connectionState) {
        ConnectionState.Disconnected -> ConnectScreen(
            statusMessage = busyState.busyReason ?: "Aguardando pedal via USB-C...",
            isBusy = busyState.isBusy,
            errorMessage = errorMessage,
            onConnectReal = { viewModel.connectReal(onCreateRealConnection) },
            onConnectFake = { viewModel.connectWith(onCreateFakeConnection()) }
        )
        is ConnectionState.Connected -> Box(modifier = Modifier.fillMaxSize()) {
            ConnectedApp(
                firmwareVersion = current.firmware.version,
                pedal = current.pedal,
                isTablet = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact,
                busyState = busyState,
                onSelectSlot = viewModel::selectSlot,
                captureState = captureState,
                onRefreshState = viewModel::refreshState,
                onStartCapture = { viewModel.startCapture(onResolveCaptureDirectory()) },
                onStopCapture = viewModel::stopCapture,
                onDisconnect = viewModel::disconnect
            )
            // Banner de diagnostico: erros durante operacoes conectadas (ex: selectSlot)
            // nao tinham nenhum lugar visivel antes - sem isso, falhas ficavam silenciosas.
            val currentError = errorMessage
            if (currentError != null) {
                Text(
                    text = currentError,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectedApp(
    firmwareVersion: String,
    pedal: PedalState,
    isTablet: Boolean,
    busyState: UiBusyState,
    onSelectSlot: (Slot) -> Unit,
    captureState: CaptureUiState,
    onRefreshState: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onDisconnect: () -> Unit
) {
    val navController = rememberNavController()
    val destinations = TopLevelDestination.entries

    if (isTablet) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                destinations.forEach { destination ->
                    val (isSelected, onClick) = rememberNavItem(navController, destination)
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = onClick,
                        icon = { Icon(destination.icon(), contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
            ConnectedNavHost(
                navController, firmwareVersion, pedal, busyState, onSelectSlot,
                captureState, onRefreshState, onStartCapture, onStopCapture, onDisconnect,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    destinations.forEach { destination ->
                        val (isSelected, onClick) = rememberNavItem(navController, destination)
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = onClick,
                            icon = { Icon(destination.icon(), contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        ) { padding ->
            ConnectedNavHost(
                navController, firmwareVersion, pedal, busyState, onSelectSlot,
                captureState, onRefreshState, onStartCapture, onStopCapture, onDisconnect,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}

@Composable
private fun ConnectedNavHost(
    navController: NavHostController,
    firmwareVersion: String,
    pedal: PedalState,
    busyState: UiBusyState,
    onSelectSlot: (Slot) -> Unit,
    captureState: CaptureUiState,
    onRefreshState: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier
    ) {
        composable(TopLevelDestination.HOME.route) {
            HomeScreen(
                firmwareVersion = firmwareVersion,
                activeSlot = pedal.activeSlot,
                presets = pedal.slots,
                isBusy = busyState.isBusy,
                busyReason = busyState.busyReason,
                onSelectSlot = onSelectSlot
            )
        }
        composable(TopLevelDestination.EDITOR.route) {
            EditorScreen(pedal = pedal)
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(
                firmwareVersion = firmwareVersion,
                isBusy = busyState.isBusy,
                busyReason = busyState.busyReason,
                isCapturing = captureState.isCapturing,
                captureFilePath = captureState.currentFilePath,
                lastCaptureFilePath = captureState.lastFilePath,
                onRefreshState = onRefreshState,
                onStartCapture = onStartCapture,
                onStopCapture = onStopCapture,
                onDisconnect = onDisconnect
            )
        }
    }
}

/**
 * Calcula o estado de selecao e a acao de clique de um item de navegacao. Os composables
 * NavigationBarItem/NavigationRailItem precisam ser chamados no escopo (Row/Column) certo,
 * entao este helper devolve apenas os dados, deixando a chamada do item no escopo correto.
 */
@Composable
private fun rememberNavItem(
    navController: NavHostController,
    destination: TopLevelDestination
): Pair<Boolean, () -> Unit> {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val isSelected = backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true
    val onClick = {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    return isSelected to onClick
}
