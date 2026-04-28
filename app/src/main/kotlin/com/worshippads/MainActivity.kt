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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.worshippads.BuildConfig
import com.worshippads.audio.AudioEngine
import com.worshippads.audio.AudioPack
import com.worshippads.audio.EqPreset
import kotlin.math.roundToInt
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
                    onEqClick = { navController.navigate("eq") },
                    backdrop = backdrop
                )
            }
            composable("settings") {
                SettingsScreen(
                    audioEngine = audioEngine,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("eq") {
                EqScreen(
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
    onEqClick: () -> Unit,
    backdrop: LayerBackdrop
) {
    val activePad by audioEngine.activePad.collectAsState()
    val isMinor by audioEngine.isMinor.collectAsState()
    val audioPack by audioEngine.audioPack.collectAsState()
    val octave by audioEngine.octave.collectAsState()
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
            // Chrome row: logo + utility icons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "Worship Pads",
                    modifier = Modifier.size(36.dp),
                    tint = Color.Unspecified
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (audioEngine.supportsOctave) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text("Transpose octave") } },
                            state = rememberTooltipState()
                        ) {
                            OctavePicker(value = octave, onChange = { audioEngine.setOctave(it) })
                        }
                    }
                    if (chartBuilderIntent != null) {
                        ChromeIconButton(
                            tooltip = "Open ChartBuilder",
                            onClick = { context.startActivity(chartBuilderIntent) }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chartbuilder),
                                contentDescription = "Open ChartBuilder",
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified
                            )
                        }
                    }
                    ChromeIconButton(
                        tooltip = "Open volume slider",
                        onClick = {
                            val audioManager = context.getSystemService(AudioManager::class.java)
                            audioManager?.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                AudioManager.ADJUST_SAME,
                                AudioManager.FLAG_SHOW_UI
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Open volume slider",
                            tint = AppColors.textSecondary
                        )
                    }
                    ChromeIconButton(
                        tooltip = "Equalizer",
                        onClick = onEqClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Equalizer",
                            tint = AppColors.textSecondary
                        )
                    }
                    ChromeIconButton(
                        tooltip = "Settings",
                        onClick = onSettingsClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = AppColors.textSecondary
                        )
                    }
                }
            }

            // Performance row: Maj/Min (only when the pack has minor keys)
            if (audioPack.hasMinor) {
                ModeToggle(
                    isMinor = isMinor,
                    onToggle = { audioEngine.setMinorMode(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioPack.entries.forEach { pack ->
                        val selected = pack == currentPack
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) AppColors.accentPrimary.copy(alpha = 0.3f)
                                    else AppColors.surfaceLight.copy(alpha = 0.5f)
                                )
                                .clickable { audioEngine.setAudioPack(pack) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pack.displayName,
                                color = if (selected) AppColors.textPrimary else AppColors.textMuted,
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = if (pack.hasMinor) "Major & Minor" else "Major only",
                                color = AppColors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
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
                subtitle = "Worship Pads v${BuildConfig.VERSION_NAME}"
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
fun EqScreen(
    audioEngine: AudioEngine,
    onBack: () -> Unit,
) {
    val bass by audioEngine.eqBass.collectAsState()
    val presence by audioEngine.eqPresence.collectAsState()
    val treble by audioEngine.eqTreble.collectAsState()
    val lowCut by audioEngine.eqLowCut.collectAsState()
    val bypassed by audioEngine.eqBypass.collectAsState()

    val activePreset = EqPreset.match(bass, presence, treble, lowCut)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .displayCutoutPadding()
            .systemBarsPadding()
            .padding(20.dp)
    ) {
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
                text = "Equalizer",
                color = AppColors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (activePreset == null) "Custom" else activePreset.label,
                color = AppColors.textMuted,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            BypassPill(
                bypassed = bypassed,
                onToggle = { audioEngine.setEqBypass(!bypassed) },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        EqGraphView(
            bassDb = bass,
            presenceDb = presence,
            trebleDb = treble,
            lowCutHz = lowCut,
            bypassed = bypassed,
            onBassChange = { audioEngine.setEqBass(it) },
            onPresenceChange = { audioEngine.setEqPresence(it) },
            onTrebleChange = { audioEngine.setEqTreble(it) },
            onLowCutChange = { audioEngine.setEqLowCut(it) },
            axisColor = AppColors.textMuted,
            curveColor = AppColors.accentPrimary,
            handleColor = AppColors.accentPrimary,
            fillColor = AppColors.accentPrimary.copy(alpha = 0.18f),
            textColor = AppColors.textPrimary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Numeric legend — live values for each band.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            EqValueReadout("HPF", "${lowCut} Hz")
            EqValueReadout("Bass", formatDb(bass))
            EqValueReadout("Pres", formatDb(presence))
            EqValueReadout("Treb", formatDb(treble))
        }

        Spacer(modifier = Modifier.height(20.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EqPreset.entries.forEach { preset ->
                PresetChip(
                    label = preset.label,
                    isActive = preset == activePreset,
                    onClick = { audioEngine.applyEqPreset(preset) },
                )
            }
        }

        if (activePreset != null && activePreset != EqPreset.FLAT) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = activePreset.blurb,
                color = AppColors.textMuted,
                fontSize = 13.sp,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = { audioEngine.applyEqPreset(EqPreset.FLAT) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Reset to Flat",
                color = AppColors.textSecondary,
                fontSize = 14.sp,
            )
        }
    }
}

private fun formatDb(db: Float): String {
    val body = "%.1f".format(db)
    return if (db > 0f) "+$body" else body
}

@Composable
private fun BypassPill(bypassed: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (bypassed) {
                    Brush.horizontalGradient(
                        colors = listOf(AppColors.accentSecondary, AppColors.accentPrimary)
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(AppColors.glassBackground, AppColors.glassBackground)
                    )
                }
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Bypass",
            color = if (bypassed) Color.White else AppColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EqValueReadout(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = AppColors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = AppColors.accentPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            // Tabular figures so digit width doesn't jitter while dragging.
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isActive) {
                    Brush.horizontalGradient(
                        colors = listOf(AppColors.accentSecondary, AppColors.accentPrimary)
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(AppColors.glassBackground, AppColors.glassBackground)
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) Color.White else AppColors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
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
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(AppColors.glassBackground)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeButton(
            text = "Major",
            isSelected = !isMinor,
            onClick = { onToggle(false) },
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            text = "Minor",
            isSelected = isMinor,
            onClick = { onToggle(true) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChromeIconButton(
    tooltip: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp)
        ) {
            content()
        }
    }
}

private fun octaveSymbol(value: Int) = buildAnnotatedString {
    if (value == 0) {
        append("0")
    } else {
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            append("8")
            withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 8.sp)) {
                append(if (value > 0) "va" else "vb")
            }
        }
    }
}

@Composable
fun OctavePicker(
    value: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.glassBackground)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OctaveSegment(label = octaveSymbol(-1), isSelected = value == -1, onClick = { onChange(-1) })
        OctaveSegment(label = octaveSymbol(0), isSelected = value == 0, onClick = { onChange(0) })
        OctaveSegment(label = octaveSymbol(1), isSelected = value == 1, onClick = { onChange(1) })
    }
}

@Composable
private fun OctaveSegment(
    label: androidx.compose.ui.text.AnnotatedString,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
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
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else AppColors.textSecondary,
            fontSize = 12.sp,
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
