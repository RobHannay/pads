package com.worshippads

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.worshippads.audio.AudioEngine
import com.worshippads.audio.AudioPack
import com.worshippads.audio.PlaybackInfo
import com.worshippads.ui.AnimatedBackground
import com.worshippads.ui.AppColors
import com.worshippads.ui.PadGrid
import com.kyant.backdrop.backdrops.LayerBackdrop

class MainActivity : ComponentActivity() {
    private lateinit var audioEngine: AudioEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge display
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audioEngine = AudioEngine(applicationContext)

        setContent {
            WorshipPadsApp(audioEngine)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.cleanup()
    }
}

@Composable
fun WorshipPadsApp(audioEngine: AudioEngine) {
    val navController = rememberNavController()
    val activePad by audioEngine.activePad.collectAsState()

    AnimatedBackground(
        isPlaying = activePad != null
    ) { backdrop ->
        NavHost(
            navController = navController,
            startDestination = "main",
            enterTransition = { slideInHorizontally(tween(300)) { it } },
            exitTransition = { slideOutHorizontally(tween(300)) { -it } },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it } },
            popExitTransition = { slideOutHorizontally(tween(300)) { it } }
        ) {
            composable("main") {
                MainScreen(
                    audioEngine = audioEngine,
                    onSettingsClick = { navController.navigate("settings") },
                    backdrop = backdrop
                )
            }
            composable("settings") {
                SettingsScreen(
                    audioEngine = audioEngine,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private const val CHARTBUILDER_PACKAGE = "com.multitracks.chartbuilder"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    audioEngine: AudioEngine,
    onSettingsClick: () -> Unit,
    backdrop: LayerBackdrop
) {
    val activePad by audioEngine.activePad.collectAsState()
    val isMinor by audioEngine.isMinor.collectAsState()
    val showDebugOverlay by audioEngine.showDebugOverlay.collectAsState()
    val startFromA by audioEngine.startFromA.collectAsState()
    val useFlats by audioEngine.useFlats.collectAsState()
    val context = LocalContext.current

    // Check if ChartBuilder is installed
    val chartBuilderIntent = remember {
        context.packageManager.getLaunchIntentForPackage(CHARTBUILDER_PACKAGE)
    }

    // Playback info state updated periodically
    var playbackInfo by remember { mutableStateOf<PlaybackInfo?>(null) }

    // Update playback info every 100ms when debug overlay is shown
    LaunchedEffect(showDebugOverlay) {
        if (showDebugOverlay) {
            while (true) {
                playbackInfo = audioEngine.getPlaybackInfo()
                delay(100)
            }
        } else {
            playbackInfo = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .displayCutoutPadding()
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "Worship Pads",
                    modifier = Modifier.size(48.dp),
                    tint = Color.Unspecified
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Minor toggle
                    ModeToggle(
                        isMinor = isMinor,
                        onToggle = { audioEngine.setMinorMode(it) }
                    )
                    // ChartBuilder button (only if installed)
                    if (chartBuilderIntent != null) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = {
                                PlainTooltip {
                                    Text("Open ChartBuilder")
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            IconButton(
                                onClick = { context.startActivity(chartBuilderIntent) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.glassBackground)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_chartbuilder),
                                    contentDescription = "Open ChartBuilder",
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        }
                    }
                    // Volume button
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("Open volume slider") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = {
                                val audioManager = context.getSystemService(AudioManager::class.java)
                                audioManager?.adjustStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    AudioManager.ADJUST_SAME,
                                    AudioManager.FLAG_SHOW_UI
                                )
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AppColors.glassBackground)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Open volume slider",
                                tint = AppColors.textSecondary
                            )
                        }
                    }
                    // Settings button
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("Settings") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AppColors.glassBackground)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = AppColors.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            PadGrid(
                activePad = activePad,
                onPadClick = { key -> audioEngine.togglePad(key) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                backdrop = backdrop,
                startFromA = startFromA,
                useFlats = useFlats
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Debug overlay
        if (showDebugOverlay && playbackInfo != null) {
            DebugOverlay(
                playbackInfo = playbackInfo!!,
                onSeek = { audioEngine.seekTo(it) },
                onDismiss = { audioEngine.setShowDebugOverlay(false) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun DebugOverlay(
    playbackInfo: PlaybackInfo,
    onSeek: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = 100f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetY.toInt()) }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    // Only allow dragging down
                    offsetY = (offsetY + delta).coerceAtLeast(0f)
                },
                onDragStopped = {
                    if (offsetY > dismissThreshold) {
                        onDismiss()
                    }
                    offsetY = 0f
                }
            )
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.glassBackground)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Player states
        playbackInfo.playerStates.forEach { state ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.label,
                    color = AppColors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(70.dp)
                )
                Text(
                    text = "${playbackInfo.formatTime(state.position)} / ${playbackInfo.formatTime(state.duration)}",
                    color = AppColors.textPrimary,
                    fontSize = 10.sp
                )
                // Volume bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AppColors.surfaceLight)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(state.volume)
                            .background(AppColors.accentPrimary)
                    )
                }
                Text(
                    text = "${(state.volume * 100).toInt()}%",
                    color = AppColors.textMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.width(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (playbackInfo.isCrossfading) {
            Text(
                text = "CROSSFADING",
                color = AppColors.accentPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Slider(
            value = if (isScrubbing) scrubPosition else playbackInfo.currentPosition.toFloat(),
            onValueChange = {
                isScrubbing = true
                scrubPosition = it
            },
            onValueChangeFinished = {
                onSeek(scrubPosition.toInt())
                isScrubbing = false
            },
            valueRange = 0f..playbackInfo.duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = AppColors.accentPrimary,
                activeTrackColor = AppColors.accentPrimary,
                inactiveTrackColor = AppColors.surfaceLight
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsScreen(
    audioEngine: AudioEngine,
    onBack: () -> Unit
) {
    var fadeInDuration by remember { mutableFloatStateOf(audioEngine.getFadeInDuration()) }
    var fadeOutDuration by remember { mutableFloatStateOf(audioEngine.getFadeOutDuration()) }
    val showDebugOverlay by audioEngine.showDebugOverlay.collectAsState()
    val startFromA by audioEngine.startFromA.collectAsState()
    val useFlats by audioEngine.useFlats.collectAsState()
    val currentPack by audioEngine.audioPack.collectAsState()
    val enableDnd by audioEngine.enableDnd.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .displayCutoutPadding()
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        // Fixed header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AppColors.glassBackground)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppColors.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Settings",
                color = AppColors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            SettingsCard(
                title = "Audio Pack",
                subtitle = "Select the pad sound pack"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.surfaceLight.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = currentPack.displayName,
                        color = AppColors.textMuted,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(
                title = "Grid",
                subtitle = "Starting key and note names"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(false to "C", true to "A").forEach { (isA, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (startFromA == isA) AppColors.accentPrimary.copy(alpha = 0.3f)
                                    else AppColors.surfaceLight.copy(alpha = 0.5f)
                                )
                                .clickable { audioEngine.setStartFromA(isA) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (startFromA == isA) AppColors.textPrimary else AppColors.textMuted,
                                fontSize = 16.sp,
                                fontWeight = if (startFromA == isA) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(false to "♯", true to "♭").forEach { (isFlat, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (useFlats == isFlat) AppColors.accentPrimary.copy(alpha = 0.3f)
                                    else AppColors.surfaceLight.copy(alpha = 0.5f)
                                )
                                .clickable { audioEngine.setUseFlats(isFlat) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (useFlats == isFlat) AppColors.textPrimary else AppColors.textMuted,
                                fontSize = 16.sp,
                                fontWeight = if (useFlats == isFlat) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(
                title = "Fade In / Crossfade",
                subtitle = "Duration when starting or switching pads"
            ) {
                DurationSlider(
                    value = fadeInDuration,
                    onValueChange = {
                        fadeInDuration = it
                        audioEngine.setFadeInDuration(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(
                title = "Fade Out",
                subtitle = "Duration when stopping a pad"
            ) {
                DurationSlider(
                    value = fadeOutDuration,
                    onValueChange = {
                        fadeOutDuration = it
                        audioEngine.setFadeOutDuration(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(
                title = "Do Not Disturb",
                subtitle = "Automatically enable DND while playing"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (enableDnd) "Enabled" else "Disabled",
                            color = AppColors.textPrimary,
                            fontSize = 16.sp
                        )
                        if (enableDnd && !audioEngine.isDndAccessGranted()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Permission required",
                                color = AppColors.accentPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                    )
                                }
                            )
                        }
                    }
                    Switch(
                        checked = enableDnd,
                        onCheckedChange = { enabled ->
                            if (enabled && !audioEngine.isDndAccessGranted()) {
                                // Enable the setting and prompt for permission
                                audioEngine.setEnableDnd(true)
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                )
                            } else {
                                audioEngine.setEnableDnd(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.accentPrimary,
                            checkedTrackColor = AppColors.accentPrimary.copy(alpha = 0.5f),
                            uncheckedThumbColor = AppColors.textMuted,
                            uncheckedTrackColor = AppColors.surfaceLight
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(
                title = "Debug Overlay",
                subtitle = "Show playback position on main screen"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showDebugOverlay) "Enabled" else "Disabled",
                        color = AppColors.textPrimary,
                        fontSize = 16.sp
                    )
                    Switch(
                        checked = showDebugOverlay,
                        onCheckedChange = { audioEngine.setShowDebugOverlay(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.accentPrimary,
                            checkedTrackColor = AppColors.accentPrimary.copy(alpha = 0.5f),
                            uncheckedThumbColor = AppColors.textMuted,
                            uncheckedTrackColor = AppColors.surfaceLight
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(
                title = "About",
                subtitle = "Worship Pads v1.0"
            ) {
                Text(
                    text = "Ambient pads for worship music.\nAudio: Karl Verkade - Bridge (Ambient Pads III)",
                    color = AppColors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun DurationSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "0.5s",
                color = AppColors.textMuted,
                fontSize = 12.sp
            )
            Text(
                text = "%.1fs".format(value),
                color = AppColors.accentPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "5.0s",
                color = AppColors.textMuted,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.5f..5f,
            colors = SliderDefaults.colors(
                thumbColor = AppColors.accentPrimary,
                activeTrackColor = AppColors.accentPrimary,
                inactiveTrackColor = AppColors.surfaceLight
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ModeToggle(
    isMinor: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(AppColors.glassBackground)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeButton(
            text = "Maj",
            isSelected = !isMinor,
            onClick = { onToggle(false) }
        )
        ModeButton(
            text = "Min",
            isSelected = isMinor,
            onClick = { onToggle(true) }
        )
    }
}

@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isSelected) {
                    Brush.horizontalGradient(
                        colors = listOf(AppColors.accentSecondary, AppColors.accentPrimary)
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Transparent)
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else AppColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color.White.copy(alpha = 0.05f))
            .padding(20.dp)
    ) {
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = AppColors.textMuted,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}
