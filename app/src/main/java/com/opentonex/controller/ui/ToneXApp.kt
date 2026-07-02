package com.opentonex.controller.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.io.File
import com.opentonex.controller.R
import com.opentonex.controller.connection.PedalConnection
import com.opentonex.controller.domain.PedalMode
import com.opentonex.controller.domain.PedalState
import com.opentonex.controller.domain.Slot
import com.opentonex.controller.repository.ConnectionState
import com.opentonex.controller.ui.connect.ConnectScreen
import com.opentonex.controller.ui.editor.EditorScreen
import com.opentonex.controller.ui.editor.EffectDetailScreen
import com.opentonex.controller.ui.editor.EffectSlotType
import com.opentonex.controller.ui.menu.MenuScreen
import com.opentonex.controller.ui.presets.PresetsScreen
import com.opentonex.controller.ui.tools.ToolsScreen

private enum class TopLevelDestination(val route: String, @StringRes val labelRes: Int) {
    EDITOR("editor", R.string.nav_editor),
    PRESETS("presets", R.string.nav_presets),
    TOOLS("tools", R.string.nav_tools),
    MENU("menu", R.string.nav_menu)
}

private fun TopLevelDestination.icon() = when (this) {
    TopLevelDestination.EDITOR -> Icons.Filled.Tune
    TopLevelDestination.PRESETS -> Icons.Filled.ViewList
    TopLevelDestination.TOOLS -> Icons.Filled.Build
    TopLevelDestination.MENU -> Icons.Filled.Menu
}

@Composable
private fun TopBrandBar(firmwareVersion: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.tusb_icon_original),
                contentDescription = "TUSB",
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "TUSB",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp)
            )
            if (firmwareVersion != null) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "FW $firmwareVersion",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )
            }
        }
    }
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
    val ampKnobs by viewModel.ampKnobs.collectAsStateWithLifecycle()
    val effectChain by viewModel.effectChain.collectAsStateWithLifecycle()
    val menuState by viewModel.menu.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startCapture(onResolveCaptureDirectory())
    }

    when (val current = connectionState) {
        ConnectionState.Disconnected -> ConnectScreen(
            statusMessage = busyState.busyReason ?: stringResource(R.string.status_waiting_usb),
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
                ampKnobs = ampKnobs,
                effectChain = effectChain,
                menuState = menuState,
                onSelectSlot = viewModel::selectSlot,
                onLoadPreset = viewModel::loadPresetToActiveSlot,
                onSwitchMode = viewModel::switchMode,
                onToggleBypass = viewModel::toggleBypass,
                onToggleCabSimBypass = viewModel::toggleCabSimBypass,
                onAmpKnobChange = viewModel::updateAmpKnob,
                onToggleEffect = viewModel::toggleEffectEnabled,
                effectDetail = viewModel::effectDetail,
                onEffectControl = viewModel::updateEffectControl,
                onMasterVolumeChange = viewModel::updateMasterVolume,
                onA4ReferenceChange = viewModel::updateA4Reference,
                captureState = captureState,
                onRefreshState = viewModel::refreshState,
                onStartCapture = { viewModel.startCapture(onResolveCaptureDirectory()) },
                onStopCapture = viewModel::stopCapture,
                onDisconnect = viewModel::disconnect
            )
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
    ampKnobs: AmpKnobUiState,
    effectChain: EffectChainUiState,
    menuState: MenuUiState,
    onSelectSlot: (Slot) -> Unit,
    onLoadPreset: (Int) -> Unit,
    onSwitchMode: (PedalMode) -> Unit,
    onToggleBypass: () -> Unit,
    onToggleCabSimBypass: () -> Unit,
    onAmpKnobChange: (AmpKnob, Float) -> Unit,
    onToggleEffect: (EffectSlotType) -> Unit,
    effectDetail: (EffectSlotType) -> EffectDetailUiState,
    onEffectControl: (EffectSlotType, EffectControl, Float) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onA4ReferenceChange: (Int) -> Unit,
    captureState: CaptureUiState,
    onRefreshState: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onDisconnect: () -> Unit
) {
    val navController = rememberNavController()
    val destinations = TopLevelDestination.entries

    if (isTablet) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBrandBar(firmwareVersion = firmwareVersion)
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    destinations.forEach { destination ->
                        val (isSelected, onClick) = rememberNavItem(navController, destination)
                        NavigationRailItem(
                            selected = isSelected,
                            onClick = onClick,
                            icon = { Icon(destination.icon(), contentDescription = stringResource(destination.labelRes)) },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
                ConnectedNavHost(
                    navController, firmwareVersion, pedal, busyState, ampKnobs, effectChain, menuState, onSelectSlot, onLoadPreset, onSwitchMode,
                    onToggleBypass, onToggleCabSimBypass, onAmpKnobChange, onToggleEffect, effectDetail, onEffectControl, onMasterVolumeChange, onA4ReferenceChange,
                    captureState, onRefreshState, onStartCapture, onStopCapture,
                    onDisconnect, modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        Scaffold(
            topBar = { TopBrandBar(firmwareVersion = firmwareVersion) },
            bottomBar = {
                NavigationBar {
                    destinations.forEach { destination ->
                        val (isSelected, onClick) = rememberNavItem(navController, destination)
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = onClick,
                            icon = { Icon(destination.icon(), contentDescription = stringResource(destination.labelRes)) },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        ) { padding ->
            ConnectedNavHost(
                navController, firmwareVersion, pedal, busyState, ampKnobs, effectChain, menuState, onSelectSlot, onLoadPreset, onSwitchMode,
                onToggleBypass, onToggleCabSimBypass, onAmpKnobChange, onToggleEffect, effectDetail, onEffectControl, onMasterVolumeChange, onA4ReferenceChange,
                captureState, onRefreshState, onStartCapture, onStopCapture,
                onDisconnect, modifier = Modifier.fillMaxSize().padding(padding)
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
    ampKnobs: AmpKnobUiState,
    effectChain: EffectChainUiState,
    menuState: MenuUiState,
    onSelectSlot: (Slot) -> Unit,
    onLoadPreset: (Int) -> Unit,
    onSwitchMode: (PedalMode) -> Unit,
    onToggleBypass: () -> Unit,
    onToggleCabSimBypass: () -> Unit,
    onAmpKnobChange: (AmpKnob, Float) -> Unit,
    onToggleEffect: (EffectSlotType) -> Unit,
    effectDetail: (EffectSlotType) -> EffectDetailUiState,
    onEffectControl: (EffectSlotType, EffectControl, Float) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onA4ReferenceChange: (Int) -> Unit,
    captureState: CaptureUiState,
    onRefreshState: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.EDITOR.route,
        modifier = modifier
    ) {
        composable(TopLevelDestination.EDITOR.route) {
            EditorScreen(
                activeSlot = pedal.activeSlot,
                activePreset = pedal.slots.getOrNull(pedal.activeSlot.ordinal),
                bypassMode = pedal.bypassMode,
                cabSimBypass = pedal.cabSimBypass,
                ampKnobs = ampKnobs,
                busyReason = busyState.busyReason,
                effectChain = effectChain.enabled,
                onAmpKnobChange = onAmpKnobChange,
                onSelectEffect = { effect -> navController.navigate("effect/${effect.name}") },
                onToggleEffect = onToggleEffect
            )
        }
        composable(
            route = "effect/{type}",
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            val typeName = backStackEntry.arguments?.getString("type") ?: EffectSlotType.MOD.name
            val effect = runCatching { EffectSlotType.valueOf(typeName) }.getOrDefault(EffectSlotType.MOD)
            EffectDetailScreen(
                effect = effect,
                enabled = effectChain.isEnabled(effect),
                detail = effectDetail(effect),
                onToggleEnabled = { onToggleEffect(effect) },
                onControlChange = { control, value -> onEffectControl(effect, control, value) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(TopLevelDestination.PRESETS.route) {
            PresetsScreen(
                activeSlot = pedal.activeSlot,
                pedalMode = pedal.pedalMode,
                bypassMode = pedal.bypassMode,
                presets = pedal.slots,
                libraryPresets = pedal.libraryPresets,
                isBusy = busyState.isBusy,
                onSelectSlot = onSelectSlot,
                onLoadPreset = onLoadPreset,
                onSwitchMode = onSwitchMode,
                onToggleBypass = onToggleBypass
            )
        }
        composable(TopLevelDestination.TOOLS.route) { ToolsScreen() }
        composable(TopLevelDestination.MENU.route) {
            MenuScreen(
                firmwareVersion = firmwareVersion,
                pedal = pedal,
                isBusy = busyState.isBusy,
                busyReason = busyState.busyReason,
                isCapturing = captureState.isCapturing,
                captureFilePath = captureState.currentFilePath,
                lastCaptureFilePath = captureState.lastFilePath,
                masterVolume = menuState.masterVolume,
                a4ReferenceOverride = menuState.a4ReferenceOverride,
                onMasterVolumeChange = onMasterVolumeChange,
                onA4ReferenceChange = onA4ReferenceChange,
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
