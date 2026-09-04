package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voice.SpeechLanguage
import com.example.voice.SpeechState

/**
 * Animated Audio Waveform displaying dynamic sound level bars responding
 * to the user's voice intensity during speech recognition.
 */
@Composable
fun VoiceVisualizerWaveform(
    soundLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 7
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_wave")
    val idlePhase by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_phase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barMultipliers = listOf(0.4f, 0.7f, 1.0f, 1.2f, 0.9f, 0.6f, 0.35f)
        for (i in 0 until barCount) {
            val multiplier = barMultipliers.getOrElse(i) { 0.7f }
            val targetFraction = if (isListening) {
                ((soundLevel * multiplier).coerceIn(0.12f, 1.0f))
            } else {
                idlePhase * multiplier * 0.4f
            }

            val animatedHeightFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
                label = "bar_height_$i"
            )

            val currentHeight = 8.dp + (24.dp * animatedHeightFraction)

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(currentHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Interactive Hands-Free Voice Listening Banner.
 * Appears when speech recognition is active to show live transcription,
 * sound waveform, status updates, and one-tap controls (Stop, Send, Cancel).
 */
@Composable
fun LiveVoiceListeningBanner(
    speechState: SpeechState,
    partialTranscript: String,
    soundLevel: Float,
    errorMessage: String?,
    isHandsFreeEnabled: Boolean,
    selectedLanguage: SpeechLanguage,
    onStopListening: () -> Unit,
    onCancelListening: () -> Unit,
    onSendNow: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = speechState != SpeechState.IDLE,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("live_voice_banner"),
            colors = CardDefaults.cardColors(
                containerColor = when (speechState) {
                    SpeechState.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                    SpeechState.PROCESSING -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f)
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                }
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header row: Status label + Waveform + Hands-free badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (speechState) {
                            SpeechState.INITIALIZING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Starting mic... / शुरू हो रहा है...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            SpeechState.LISTENING -> {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935))
                                )
                                Text(
                                    text = "Listening... / सुन रहा हूँ...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            SpeechState.PROCESSING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Transcribing speech... / समझ रहा हूँ...",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            SpeechState.ERROR -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Speech Notice",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> {}
                        }
                    }

                    // Audio visualizer wave (active when listening)
                    if (speechState == SpeechState.LISTENING) {
                        VoiceVisualizerWaveform(
                            soundLevel = soundLevel,
                            isListening = true
                        )
                    }

                    // Hands-Free Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = if (isHandsFreeEnabled) "⚡ Hands-Free ON" else "Manual Send",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Transcript Preview Area
                when {
                    speechState == SpeechState.ERROR -> {
                        Text(
                            text = errorMessage ?: "Unable to capture audio. Please try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    partialTranscript.isNotBlank() -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "“$partialTranscript”",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = when (selectedLanguage) {
                                SpeechLanguage.HINDI -> "कुछ बोलें, जैसे 'YouTube खोलो' या 'Google पर search करो'"
                                SpeechLanguage.ENGLISH -> "Speak a command, e.g. 'Open YouTube' or 'Check Weather'"
                                SpeechLanguage.BILINGUAL -> "Speak any command (Hindi / English), e.g. 'YouTube खोलो' or 'Open Settings'"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                if (speechState == SpeechState.PROCESSING) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Action Controls Row: Cancel, Stop, Send Now
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onCancelListening,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (speechState == SpeechState.ERROR) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        ),
                        modifier = Modifier.testTag("voice_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel", fontSize = 12.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (speechState == SpeechState.LISTENING) {
                            FilledTonalButton(
                                onClick = onStopListening,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("voice_stop_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Done Speaking",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (partialTranscript.isNotBlank()) {
                            FilledTonalButton(
                                onClick = { onSendNow(partialTranscript) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("voice_send_now_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Command Now",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Microphone button with visual pulse animation during speech recognition.
 */
@Composable
fun VoiceMicButton(
    isListening: Boolean,
    isProcessing: Boolean,
    soundLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_ring")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring when listening
        if (isListening) {
            val dynamicScale = (pulseScale + (soundLevel * 0.3f)).coerceIn(1.0f, 1.6f)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(dynamicScale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            )
        }

        FilledIconButton(
            onClick = onClick,
            shape = CircleShape,
            modifier = Modifier
                .size(48.dp)
                .testTag("voice_mic_button"),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = when {
                    isListening -> MaterialTheme.colorScheme.error
                    isProcessing -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = when {
                    isListening -> MaterialTheme.colorScheme.onError
                    isProcessing -> MaterialTheme.colorScheme.onSecondary
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        ) {
            when {
                isProcessing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
                isListening -> {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Voice Listening"
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Start Voice Command (Hands-Free)"
                    )
                }
            }
        }
    }
}

/**
 * Banner displayed when Myra is actively speaking response text aloud.
 * Shows animated speaking indicator and a quick "Stop" button.
 */
@Composable
fun LiveVoiceSpeakingBanner(
    isSpeaking: Boolean,
    onStopSpeaking: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isSpeaking,
        enter = fadeIn(animationSpec = tween(200)) + expandVertically(),
        exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("voice_speaking_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Myra is speaking",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "मायरा बोल रही है... (Speaking)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        VoiceVisualizerWaveform(
                            soundLevel = 55f,
                            isListening = true
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onStopSpeaking,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("stop_speaking_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("रोकें", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Small speaker button on assistant message cards to re-read / speak out loud.
 */
@Composable
fun VoiceSpeakerIconButton(
    isCurrentSpeaking: Boolean,
    onSpeak: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledIconButton(
        onClick = {
            if (isCurrentSpeaking) onStop() else onSpeak()
        },
        shape = CircleShape,
        modifier = modifier
            .size(32.dp)
            .testTag("bubble_speak_button"),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isCurrentSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isCurrentSpeaking) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(
            imageVector = if (isCurrentSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
            contentDescription = if (isCurrentSpeaking) "Stop Speaking" else "Listen to Response",
            modifier = Modifier.size(16.dp)
        )
    }
}
