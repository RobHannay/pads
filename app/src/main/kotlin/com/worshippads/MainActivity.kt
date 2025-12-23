package com.worshippads

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle

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
    val hazeState = remember { HazeState() }

    AnimatedBackground(hazeState = hazeState) {
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
                    hazeState = hazeState
                )
            }
            composable("settings") {
                SettingsScreen(
                    audioEngine = audioEngine,
                    onBack = { navController.popBackStack() },
                    hazeState = hazeState
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    audioEngine: AudioEngine,
    onSettingsClick: () -> Unit,
    hazeState: HazeState
) {
    val activePad by audioEngine.activePad.collectAsState()
    val isMinor by audioEngine.isMinor.collectAsState()
    val showDebugOverlay by audioEngine.showDebugOverlay.collectAsState()

    // Playback info state updated periodically
    var playbackInfo by remember { mutableStateOf<PlaybackInfo?>(null) }

    // Update playback info every 100ms when debug overlay is shown
    LaunchedEffect(showDebugOverlay, activePad) {
        if (showDebugOverlay && activePad != null) {
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
                Text(
                    text = "Worship Pads",
                    color = AppColors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
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
                    // Settings button
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

            Spacer(modifier = Modifier.height(20.dp))

            PadGrid(
                activePad = activePad,
                onPadClick = { key -> audioEngine.togglePad(key) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                hazeState = hazeState
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Debug overlay
        if (showDebugOverlay && playbackInfo != null) {
            DebugOverlay(
                playbackInfo = playbackInfo!!,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun DebugOverlay(
    playbackInfo: PlaybackInfo,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.glassBackground)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Position",
                color = AppColors.textMuted,
                fontSize = 10.sp
            )
            Text(
                text = playbackInfo.formatTime(playbackInfo.currentPosition),
                color = AppColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Duration",
                color = AppColors.textMuted,
                fontSize = 10.sp
            )
            Text(
                text = playbackInfo.formatTime(playbackInfo.duration),
                color = AppColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Remaining",
                color = AppColors.textMuted,
                fontSize = 10.sp
            )
            Text(
                text = playbackInfo.formatTime(playbackInfo.remaining),
                color = AppColors.accentPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SettingsScreen(
    audioEngine: AudioEngine,
    onBack: () -> Unit,
    hazeState: HazeState
) {
    var fadeInDuration by remember { mutableFloatStateOf(audioEngine.getFadeInDuration()) }
    var fadeOutDuration by remember { mutableFloatStateOf(audioEngine.getFadeOutDuration()) }
    val showDebugOverlay by audioEngine.showDebugOverlay.collectAsState()
    val currentPack by audioEngine.audioPack.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
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

            SettingsCard(
                title = "Audio Pack",
                subtitle = "Select the pad sound pack",
                hazeState = hazeState
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
                title = "Fade In / Crossfade",
                subtitle = "Duration when starting or switching pads",
                hazeState = hazeState
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
                subtitle = "Duration when stopping a pad",
                hazeState = hazeState
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
                title = "Debug Overlay",
                subtitle = "Show playback position on main screen",
                hazeState = hazeState
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
                subtitle = "Worship Pads v1.0",
                hazeState = hazeState
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
    hazeState: HazeState? = null,
    content: @Composable () -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = AppColors.glassBackground.copy(alpha = 0.4f),
                            tints = emptyList(),
                            blurRadius = 20.dp
                        )
                    )
                } else Modifier
            )
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
