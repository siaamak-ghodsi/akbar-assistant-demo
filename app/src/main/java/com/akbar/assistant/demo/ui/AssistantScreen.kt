package com.akbar.assistant.demo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akbar.assistant.demo.AppLanguage
import com.akbar.assistant.demo.AssistantState
import com.akbar.assistant.demo.AssistantUiState
import com.akbar.assistant.demo.commands.AssistantCommand
import kotlin.math.cos
import kotlin.math.sin

private val Void = Color(0xFF07090F)
private val Mist = Color(0xFF121624)
private val Pearl = Color(0xFFF2F4F8)
private val Soft = Color(0xFFB8C0D0)
private val Dim = Color(0xFF7A8498)
private val Glow = Color(0xFF9EC5FF)
/** Warm champagne speaking accent — calm trust, not purple-AI cliché. */
private val Speak = Color(0xFFE8D5B5)
private val Ok = Color(0xFF8EE0C4)
private val Caution = Color(0xFFFFB4A8)

@Composable
fun AkbarAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Glow,
            background = Void,
            surface = Mist,
            onPrimary = Color.Black,
            onBackground = Pearl,
            onSurface = Pearl
        ),
        content = content
    )
}

@Composable
fun AssistantScreen(
    state: AssistantUiState,
    onToggleTestMode: () -> Unit,
    onSimulateWake: (AppLanguage) -> Unit,
    onTestCommand: (AssistantCommand) -> Unit,
    onRequestMicPermission: () -> Unit = {}
) {
    val listening = state.state == AssistantState.LISTENING_WAKE ||
        state.state == AssistantState.LISTENING_COMMAND ||
        state.state == AssistantState.ACTIVATED
    val speaking = state.state == AssistantState.SPEAKING
    val processing = state.state == AssistantState.PROCESSING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Void, Mist, Color(0xFF0C101C))))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Glow.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.32f),
                    radius = size.minDimension * 0.62f
                ),
                radius = size.minDimension * 0.62f,
                center = Offset(size.width * 0.5f, size.height * 0.32f)
            )
        }

        if (!state.permissionGranted) {
            PermissionGate(onRequest = onRequestMicPermission)
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "اکبر",
                color = Pearl.copy(alpha = 0.94f),
                fontSize = 34.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.weight(0.18f))

            PresenceOrb(
                listening = listening,
                speaking = speaking,
                processing = processing,
                rms = state.rmsLevel
            )

            Spacer(modifier = Modifier.height(36.dp))

            AnimatedContent(
                targetState = state.statusText,
                transitionSpec = {
                    (fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(280))) togetherWith
                        (fadeOut(tween(180)) +
                            scaleOut(targetScale = 1.01f, animationSpec = tween(180)))
                },
                label = "status"
            ) { status ->
                Text(
                    text = status,
                    color = Pearl,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedContent(
                targetState = state.hintText,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                label = "hint"
            ) { hint ->
                if (hint.isNotBlank()) {
                    Text(
                        text = hint,
                        color = Soft.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Captions only while awaiting / processing a command — never during quiet wake.
            AnimatedVisibility(
                visible = state.lastHeard.isNotBlank() &&
                    (state.state == AssistantState.LISTENING_COMMAND ||
                        state.state == AssistantState.ACTIVATED ||
                        state.state == AssistantState.PROCESSING),
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(220))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = state.lastHeard,
                        color = Dim,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.fillMaxWidth(0.86f)
                    )
                }
            }

            AnimatedVisibility(
                visible = state.lightOn,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(20.dp))
                    SoftPill(
                        text = if (state.language == AppLanguage.PERSIAN) {
                            "چراغ‌قوه روشن"
                        } else {
                            "Flashlight on"
                        },
                        color = Ok
                    )
                }
            }

            state.errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = err,
                    color = Caution,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }

            Spacer(modifier = Modifier.weight(0.22f))

            // Quiet affordance — not a loud test CTA.
            Text(
                text = if (state.testModeVisible) "×" else "···",
                color = Dim.copy(alpha = 0.55f),
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onToggleTestMode
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )

            AnimatedVisibility(visible = state.testModeVisible) {
                TestLab(
                    onSimulateWake = onSimulateWake,
                    onTestCommand = onTestCommand
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "اکبر",
            color = Pearl,
            fontSize = 42.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "برای شنیدن شما، به میکروفون نیاز دارم",
            color = Soft,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Microphone access is needed to listen",
            color = Dim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Pearl.copy(alpha = 0.08f))
                .border(1.dp, Pearl.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                .clickable(onClick = onRequest)
                .padding(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Text(
                text = "اجازه دسترسی",
                color = Pearl,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PresenceOrb(
    listening: Boolean,
    speaking: Boolean,
    processing: Boolean,
    rms: Float
) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val breath by infinite.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )
    val energy by animateFloatAsState(
        targetValue = when {
            speaking -> 0.82f
            processing -> 0.52f
            listening -> 0.32f + rms * 0.48f
            else -> 0.16f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "energy"
    )
    val core = when {
        speaking -> Speak
        processing -> Glow
        listening -> Glow
        else -> Soft.copy(alpha = 0.35f)
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Canvas(
            modifier = Modifier
                .size(220.dp)
                .scale(if (listening || speaking || processing) breath else 1f)
        ) {
            val min = size.minDimension
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(core.copy(alpha = 0.16f * energy), Color.Transparent)
                ),
                radius = min * (0.48f + energy * 0.05f)
            )
            drawCircle(
                color = core.copy(alpha = 0.20f + energy * 0.22f),
                radius = min * 0.34f,
                style = Stroke(width = (2.2f + energy * 2.6f).dp.toPx())
            )
            drawCircle(
                color = core.copy(alpha = 0.50f),
                radius = min * 0.22f,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.48f * energy),
                        core.copy(alpha = 0.32f * energy),
                        Color.Transparent
                    )
                ),
                radius = min * (0.11f + energy * 0.05f)
            )
            if (listening || speaking || processing) {
                val rad = Math.toRadians(spin.toDouble())
                val orbit = min * 0.30f
                val cx = center.x + (orbit * cos(rad)).toFloat()
                val cy = center.y + (orbit * sin(rad)).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = 0.50f),
                    radius = 2.2.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

@Composable
private fun SoftPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestLab(
    onSimulateWake: (AppLanguage) -> Unit,
    onTestCommand: (AssistantCommand) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Pearl.copy(alpha = 0.04f))
            .border(1.dp, Pearl.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "آزمایش بی‌صدا", color = Dim, fontSize = 12.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuietChip("هی اکبر") { onSimulateWake(AppLanguage.PERSIAN) }
            QuietChip("Hey Akbar") { onSimulateWake(AppLanguage.ENGLISH) }
            QuietChip("ساعت") { onTestCommand(AssistantCommand.TellTime(AppLanguage.PERSIAN)) }
            QuietChip("هوا") { onTestCommand(AssistantCommand.Weather(AppLanguage.PERSIAN)) }
            QuietChip("چراغ روشن") { onTestCommand(AssistantCommand.LightOn(AppLanguage.PERSIAN)) }
            QuietChip("چراغ خاموش") { onTestCommand(AssistantCommand.LightOff(AppLanguage.PERSIAN)) }
            QuietChip("Time") { onTestCommand(AssistantCommand.TellTime(AppLanguage.ENGLISH)) }
            QuietChip("Weather") { onTestCommand(AssistantCommand.Weather(AppLanguage.ENGLISH)) }
            QuietChip("Light on") { onTestCommand(AssistantCommand.LightOn(AppLanguage.ENGLISH)) }
            QuietChip("Light off") { onTestCommand(AssistantCommand.LightOff(AppLanguage.ENGLISH)) }
        }
    }
}

@Composable
private fun QuietChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Pearl.copy(alpha = 0.05f))
            .border(1.dp, Pearl.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = Soft, fontSize = 12.sp)
    }
}
