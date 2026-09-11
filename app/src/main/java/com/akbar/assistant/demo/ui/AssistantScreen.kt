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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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

private val Ink = Color(0xFF0B1220)
private val Deep = Color(0xFF122033)
private val Card = Color(0xFF18263A)
private val Teal = Color(0xFF2DD4BF)
private val Blue = Color(0xFF60A5FA)
private val Cream = Color(0xFFE8EEF7)
private val Quiet = Color(0xFF93A4BC)
private val Warn = Color(0xFFFCA5A5)

@Composable
fun AkbarAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal,
            background = Ink,
            surface = Card,
            onPrimary = Color.Black,
            onBackground = Cream,
            onSurface = Cream
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
    val live = state.state == AssistantState.LISTENING_WAKE ||
        state.state == AssistantState.ACTIVATED ||
        state.state == AssistantState.LISTENING_COMMAND ||
        state.state == AssistantState.PROCESSING ||
        state.state == AssistantState.SPEAKING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink, Deep, Color(0xFF0E1C2A))))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Teal.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.12f),
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.18f, size.height * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Blue.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * 0.22f),
                    radius = size.minDimension * 0.5f
                ),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width * 0.88f, size.height * 0.22f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
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
                        color = Cream,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 48.sp
                    )
                    Text(text = "دستیار صوتی", color = Quiet, fontSize = 15.sp)
                }
                LanguageChip(language = state.language)
            }

            Spacer(modifier = Modifier.height(40.dp))

            VoiceOrb(
                active = live,
                speaking = state.state == AssistantState.SPEAKING,
                processing = state.state == AssistantState.PROCESSING,
                rms = state.rmsLevel
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = state.statusText,
                color = Cream,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.hintText,
                color = Quiet,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.lastReply.isNotBlank() &&
                state.state != AssistantState.LISTENING_WAKE &&
                state.state != AssistantState.IDLE
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.lastReply,
                    color = Teal,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            StatusRow(lightOn = state.lightOn, state = state.state)
            Spacer(modifier = Modifier.height(18.dp))
            TranscriptCard(lastHeard = state.lastHeard)

            if (!state.permissionGranted) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "دسترسی میکروفون را فعال کنید\nAllow microphone access",
                    color = Warn,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }

            state.errorMessage?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = it, color = Warn, fontSize = 12.sp, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(18.dp))
            TextButton(onClick = onToggleTestMode) {
                Text(
                    text = if (state.testModeVisible) "بستن تست" else "حالت تست",
                    color = Teal
                )
            }

            if (state.testModeVisible) {
                TestPanel(
                    onSimulateWake = onSimulateWake,
                    onTestCommand = onTestCommand
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LanguageChip(language: AppLanguage) {
    val label = if (language == AppLanguage.PERSIAN) "FA" else "EN"
    val color = if (language == AppLanguage.PERSIAN) Teal else Blue
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun VoiceOrb(active: Boolean, speaking: Boolean, processing: Boolean, rms: Float) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val pulse by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val ring by animateColorAsState(
        targetValue = when {
            speaking -> Teal
            processing -> Blue
            active -> Blue.copy(alpha = 0.95f)
            else -> Color(0xFF334155)
        },
        label = "ring"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
        Canvas(
            modifier = Modifier
                .size(210.dp)
                .scale(if (active || speaking || processing) pulse else 1f)
        ) {
            drawCircle(
                color = ring.copy(alpha = 0.14f),
                radius = size.minDimension / 2.05f,
                style = Stroke(width = 12.dp.toPx())
            )
            drawCircle(
                color = ring,
                radius = size.minDimension / 2.4f,
                style = Stroke(width = 3.dp.toPx())
            )
            val bars = 12
            val barW = 4.2.dp.toPx()
            val gap = 6.5.dp.toPx()
            val total = bars * barW + (bars - 1) * gap
            val startX = (size.width - total) / 2f
            val midY = size.height / 2f
            for (i in 0 until bars) {
                val amp = 0.28f + 0.72f * ((sin((phase + i * 0.48f).toDouble()) + 1) / 2f).toFloat()
                val level = if (active || speaking || processing) amp * (0.3f + rms * 0.9f) else 0.16f
                val h = 12.dp.toPx() + level * 52.dp.toPx()
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
private fun StatusRow(lightOn: Boolean, state: AssistantState) {
    val mode = when (state) {
        AssistantState.LISTENING_WAKE -> "آماده"
        AssistantState.ACTIVATED, AssistantState.LISTENING_COMMAND -> "فعال"
        AssistantState.PROCESSING -> "پردازش"
        AssistantState.SPEAKING -> "در حال پاسخ"
        AssistantState.IDLE -> "متوقف"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
    ) {
        MetaChip(label = mode)
        MetaChip(
            label = if (lightOn) "چراغ‌قوه روشن" else "چراغ‌قوه خاموش",
            accent = if (lightOn) Teal else Quiet
        )
    }
}

@Composable
private fun MetaChip(label: String, accent: Color = Quiet) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Card.copy(alpha = 0.9f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TranscriptCard(lastHeard: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(text = "شنیده‌شده", color = Quiet, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = lastHeard.ifBlank { "—" },
            color = Cream,
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
            .background(Card)
            .border(1.dp, Teal.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "Test Mode", color = Teal, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip("هی اکبر") { onSimulateWake(AppLanguage.PERSIAN) }
            Chip("Hey Akbar") { onSimulateWake(AppLanguage.ENGLISH) }
        }
        Text(text = "فارسی", color = Quiet, fontSize = 12.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip("ساعت؟") { onTestCommand(AssistantCommand.TellTime(AppLanguage.PERSIAN)) }
            Chip("هوا؟") { onTestCommand(AssistantCommand.Weather(AppLanguage.PERSIAN)) }
            Chip("چراغ روشن") { onTestCommand(AssistantCommand.LightOn(AppLanguage.PERSIAN)) }
            Chip("چراغ خاموش") { onTestCommand(AssistantCommand.LightOff(AppLanguage.PERSIAN)) }
        }
        Text(text = "English", color = Quiet, fontSize = 12.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip("Time?") { onTestCommand(AssistantCommand.TellTime(AppLanguage.ENGLISH)) }
            Chip("Weather?") { onTestCommand(AssistantCommand.Weather(AppLanguage.ENGLISH)) }
            Chip("Light on") { onTestCommand(AssistantCommand.LightOn(AppLanguage.ENGLISH)) }
            Chip("Light off") { onTestCommand(AssistantCommand.LightOff(AppLanguage.ENGLISH)) }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
        border = BorderStroke(1.dp, Teal.copy(alpha = 0.35f)),
        modifier = Modifier.height(40.dp)
    ) {
        Text(text = label, fontSize = 12.sp, maxLines = 1)
    }
}
