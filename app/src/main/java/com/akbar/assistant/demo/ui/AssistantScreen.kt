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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akbar.assistant.demo.AppLanguage
import com.akbar.assistant.demo.AssistantState
import com.akbar.assistant.demo.AssistantUiState
import com.akbar.assistant.demo.commands.AssistantCommand
import kotlin.math.sin

private val Bg = Color(0xFF0B0F14)
private val SurfaceDark = Color(0xFF151B24)
private val Accent = Color(0xFF3DDC97)
private val ListeningBlue = Color(0xFF4FC3F7)
private val TextPrimary = Color(0xFFF2F5F8)
private val TextSecondary = Color(0xFF9AA7B5)
private val LightOn = Color(0xFFFFD54F)
private val LightOff = Color(0xFF4A5562)

@Composable
fun AkbarAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = SurfaceDark,
            onPrimary = Color.Black,
            onBackground = TextPrimary,
            onSurface = TextPrimary
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
    onToggleLightManual: () -> Unit
) {
    val listening = state.state == AssistantState.LISTENING_COMMAND ||
        state.state == AssistantState.ACTIVATED ||
        state.state == AssistantState.LISTENING_WAKE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B0F14), Color(0xFF101820), Color(0xFF0B1210))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Akbar Assistant",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "اکبر دستیار", color = TextSecondary, fontSize = 14.sp)
                }
                LanguageBadge(language = state.language)
            }

            Spacer(modifier = Modifier.height(36.dp))

            ListeningIndicator(
                active = listening,
                processing = state.state == AssistantState.PROCESSING || state.state == AssistantState.SPEAKING,
                rms = state.rmsLevel
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = state.statusText,
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.lastReply.isNotBlank() &&
                state.state != AssistantState.LISTENING_WAKE &&
                state.state != AssistantState.IDLE
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = state.lastReply,
                    color = Accent,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            LightBulbVisual(isOn = state.lightOn, onClick = onToggleLightManual)
            Spacer(modifier = Modifier.height(28.dp))
            HeardPanel(lastHeard = state.lastHeard)

            if (!state.permissionGranted) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Microphone permission is required.\nدسترسی میکروفون لازم است.",
                    color = Color(0xFFEF5350),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }

            state.errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = Color(0xFFEF5350), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onToggleTestMode) {
                Text(
                    text = if (state.testModeVisible) "Hide Test Mode" else "Test Mode",
                    color = Accent
                )
            }

            if (state.testModeVisible) {
                TestModePanel(
                    onSimulateWake = onSimulateWake,
                    onTestCommand = onTestCommand
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LanguageBadge(language: AppLanguage) {
    val label = if (language == AppLanguage.PERSIAN) "FA" else "EN"
    val color = if (language == AppLanguage.PERSIAN) Accent else ListeningBlue
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun ListeningIndicator(active: Boolean, processing: Boolean, rms: Float) {
    val infinite = rememberInfiniteTransition(label = "listen")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )
    val ringColor by animateColorAsState(
        targetValue = when {
            processing -> Accent
            active -> ListeningBlue
            else -> Color(0xFF2A3440)
        },
        label = "ringColor"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
        Canvas(
            modifier = Modifier
                .size(180.dp)
                .scale(if (active || processing) pulse else 1f)
        ) {
            val stroke = 6.dp.toPx()
            drawCircle(
                color = ringColor.copy(alpha = 0.25f),
                radius = size.minDimension / 2.2f,
                style = Stroke(width = stroke)
            )
            drawCircle(
                color = ringColor,
                radius = size.minDimension / 2.6f,
                style = Stroke(width = stroke * 0.7f)
            )
            val barCount = 9
            val barWidth = 5.dp.toPx()
            val spacing = 8.dp.toPx()
            val totalWidth = barCount * barWidth + (barCount - 1) * spacing
            val startX = (size.width - totalWidth) / 2f
            val midY = size.height / 2f
            for (i in 0 until barCount) {
                val amp = (0.35f + 0.65f * ((sin((wavePhase + i * 0.55f).toDouble()) + 1) / 2f).toFloat())
                val level = if (active || processing) (amp * (0.4f + rms * 0.8f)) else 0.2f
                val barHeight = 18.dp.toPx() + level * 42.dp.toPx()
                val x = startX + i * (barWidth + spacing) + barWidth / 2f
                drawLine(
                    color = ringColor,
                    start = Offset(x, midY - barHeight / 2f),
                    end = Offset(x, midY + barHeight / 2f),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun LightBulbVisual(isOn: Boolean, onClick: () -> Unit) {
    val glow by animateColorAsState(targetValue = if (isOn) LightOn else LightOff, label = "bulb")
    val infinite = rememberInfiniteTransition(label = "glow")
    val glowScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (isOn) 1.08f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "glowScale"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .scale(if (isOn) glowScale else 1f)
                .clip(CircleShape)
                .background(if (isOn) LightOn.copy(alpha = 0.18f) else Color(0xFF1A222C))
                .clickable(onClick = onClick)
        ) {
            Icon(
                imageVector = if (isOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                contentDescription = if (isOn) "Light on" else "Light off",
                tint = glow,
                modifier = Modifier.size(84.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (isOn) "Light ON / چراغ روشن" else "Light OFF / چراغ خاموش",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun HeardPanel(lastHeard: String) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Last recognized / آخرین تشخیص", color = TextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = lastHeard.ifBlank { "—" }, color = TextPrimary, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TestModePanel(
    onSimulateWake: (AppLanguage) -> Unit,
    onTestCommand: (AssistantCommand) -> Unit
) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Test Mode — tap to run without voice",
                color = Accent,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallAction("هی اکبر") { onSimulateWake(AppLanguage.PERSIAN) }
                SmallAction("Hey Akbar") { onSimulateWake(AppLanguage.ENGLISH) }
            }
            Text(text = "Persian commands", color = TextSecondary, fontSize = 12.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallAction("ساعت چنده؟") { onTestCommand(AssistantCommand.TellTime(AppLanguage.PERSIAN)) }
                SmallAction("هوا چطوره؟") { onTestCommand(AssistantCommand.Weather(AppLanguage.PERSIAN)) }
                SmallAction("چراغ روشن") { onTestCommand(AssistantCommand.LightOn(AppLanguage.PERSIAN)) }
                SmallAction("چراغ خاموش") { onTestCommand(AssistantCommand.LightOff(AppLanguage.PERSIAN)) }
            }
            Text(text = "English commands", color = TextSecondary, fontSize = 12.sp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallAction("What time is it?") { onTestCommand(AssistantCommand.TellTime(AppLanguage.ENGLISH)) }
                SmallAction("What's the weather?") { onTestCommand(AssistantCommand.Weather(AppLanguage.ENGLISH)) }
                SmallAction("Turn on the light") { onTestCommand(AssistantCommand.LightOn(AppLanguage.ENGLISH)) }
                SmallAction("Turn off the light") { onTestCommand(AssistantCommand.LightOff(AppLanguage.ENGLISH)) }
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.45f)),
        modifier = Modifier.height(40.dp)
    ) {
        Text(text = label, fontSize = 12.sp, maxLines = 1)
    }
}
