package com.akbar.assistant.demo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.sin

private val Night = Color(0xFF071018)
private val Deep = Color(0xFF0C1822)
private val Panel = Color(0xFF132231)
private val Mint = Color(0xFF3DDC97)
private val Sky = Color(0xFF5EC8F2)
private val Soft = Color(0xFFD7E2EC)
private val Muted = Color(0xFF8FA3B5)
private val BulbOn = Color(0xFFFFD56A)
private val BulbOff = Color(0xFF4D5C6A)

@Composable
fun AkbarAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            background = Night,
            surface = Panel,
            onPrimary = Color.Black,
            onBackground = Soft,
            onSurface = Soft
        ),
        content = content
    )
}

@Composable
fun AssistantScreen(
    state: AssistantUiState,
    onToggleTestMode: () -> Unit,
    onSimulateWake: (AppLanguage) -> Unit,
    onTestCommand: (AssistantCommand) -> Unit
) {
    val active = state.state == AssistantState.LISTENING_WAKE ||
        state.state == AssistantState.ACTIVATED ||
        state.state == AssistantState.LISTENING_COMMAND ||
        state.state == AssistantState.PROCESSING ||
        state.state == AssistantState.SPEAKING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Night, Deep, Color(0xFF0A1A16))
                )
            )
    ) {
        // Soft atmosphere blobs
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Mint.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.15f),
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.2f, size.height * 0.15f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Sky.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.28f),
                    radius = size.minDimension * 0.5f
                ),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width * 0.85f, size.height * 0.28f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "اکبر",
                        color = Soft,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 44.sp
                    )
                    Text(
                        text = "Akbar Assistant",
                        color = Muted,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                LanguageChip(language = state.language)
            }

            Spacer(modifier = Modifier.height(36.dp))

            ListeningOrb(
                active = active,
                processing = state.state == AssistantState.PROCESSING || state.state == AssistantState.SPEAKING,
                rms = state.rmsLevel
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = state.statusText,
                color = Soft,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 38.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.hintText,
                color = Muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.lastReply.isNotBlank() &&
                state.state != AssistantState.LISTENING_WAKE &&
                state.state != AssistantState.IDLE
            ) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = state.lastReply,
                    color = Mint,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(34.dp))

            // Display-only light status (NOT a toggle control)
            LightStatus(isOn = state.lightOn)

            Spacer(modifier = Modifier.height(28.dp))

            HeardCard(lastHeard = state.lastHeard)

            if (!state.permissionGranted) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "برای شروع، دسترسی میکروفون را بدهید\nAllow microphone access to begin",
                    color = Color(0xFFFF8A80),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }

            state.errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = Color(0xFFFF8A80), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
            TextButton(onClick = onToggleTestMode) {
                Text(
                    text = if (state.testModeVisible) "بستن حالت تست / Hide Test" else "حالت تست / Test Mode",
                    color = Mint
                )
            }

            if (state.testModeVisible) {
                TestPanel(
                    onSimulateWake = onSimulateWake,
                    onTestCommand = onTestCommand
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LanguageChip(language: AppLanguage) {
    val label = if (language == AppLanguage.PERSIAN) "FA" else "EN"
    val color = if (language == AppLanguage.PERSIAN) Mint else Sky
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ListeningOrb(active: Boolean, processing: Boolean, rms: Float) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val ring by animateColorAsState(
        targetValue = when {
            processing -> Mint
            active -> Sky
            else -> Color(0xFF2A3A48)
        },
        label = "ring"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Canvas(
            modifier = Modifier
                .size(200.dp)
                .scale(if (active || processing) pulse else 1f)
        ) {
            drawCircle(
                color = ring.copy(alpha = 0.16f),
                radius = size.minDimension / 2.05f,
                style = Stroke(width = 10.dp.toPx())
            )
            drawCircle(
                color = ring,
                radius = size.minDimension / 2.45f,
                style = Stroke(width = 3.5.dp.toPx())
            )
            val bars = 11
            val barW = 4.5.dp.toPx()
            val gap = 7.dp.toPx()
            val total = bars * barW + (bars - 1) * gap
            val startX = (size.width - total) / 2f
            val midY = size.height / 2f
            for (i in 0 until bars) {
                val amp = 0.3f + 0.7f * ((sin((phase + i * 0.5f).toDouble()) + 1) / 2f).toFloat()
                val level = if (active || processing) amp * (0.35f + rms * 0.85f) else 0.18f
                val h = 14.dp.toPx() + level * 48.dp.toPx()
                val x = startX + i * (barW + gap) + barW / 2f
                drawLine(
                    color = ring,
                    start = Offset(x, midY - h / 2f),
                    end = Offset(x, midY + h / 2f),
                    strokeWidth = barW,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun LightStatus(isOn: Boolean) {
    val glow by animateColorAsState(if (isOn) BulbOn else BulbOff, label = "bulb")
    val infinite = rememberInfiniteTransition(label = "bulbPulse")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (isOn) 1.06f else 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "bulbScale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(128.dp)
                .scale(if (isOn) scale else 1f)
                .clip(CircleShape)
                .background(if (isOn) BulbOn.copy(alpha = 0.16f) else Panel)
                .border(
                    1.dp,
                    if (isOn) BulbOn.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = glow,
                modifier = Modifier.size(72.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isOn) "چراغ روشن است · Light is ON" else "چراغ خاموش است · Light is OFF",
            color = Muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HeardCard(lastHeard: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Panel.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Text(text = "شنیدم / Heard", color = Muted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = lastHeard.ifBlank { "—" },
            color = Soft,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestPanel(
    onSimulateWake: (AppLanguage) -> Unit,
    onTestCommand: (AssistantCommand) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, Mint.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Test Mode — بدون صدا",
            color = Mint,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip("هی اکبر") { onSimulateWake(AppLanguage.PERSIAN) }
            Chip("Hey Akbar") { onSimulateWake(AppLanguage.ENGLISH) }
        }
        Text(text = "فارسی", color = Muted, fontSize = 12.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip("ساعت چنده؟") { onTestCommand(AssistantCommand.TellTime(AppLanguage.PERSIAN)) }
            Chip("هوا چطوره؟") { onTestCommand(AssistantCommand.Weather(AppLanguage.PERSIAN)) }
            Chip("چراغ روشن") { onTestCommand(AssistantCommand.LightOn(AppLanguage.PERSIAN)) }
            Chip("چراغ خاموش") { onTestCommand(AssistantCommand.LightOff(AppLanguage.PERSIAN)) }
        }
        Text(text = "English", color = Muted, fontSize = 12.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip("What time is it?") { onTestCommand(AssistantCommand.TellTime(AppLanguage.ENGLISH)) }
            Chip("What's the weather?") { onTestCommand(AssistantCommand.Weather(AppLanguage.ENGLISH)) }
            Chip("Turn on the light") { onTestCommand(AssistantCommand.LightOn(AppLanguage.ENGLISH)) }
            Chip("Turn off the light") { onTestCommand(AssistantCommand.LightOff(AppLanguage.ENGLISH)) }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Soft),
        border = BorderStroke(1.dp, Mint.copy(alpha = 0.35f)),
        modifier = Modifier.height(40.dp)
    ) {
        Text(text = label, fontSize = 12.sp, maxLines = 1)
    }
}
