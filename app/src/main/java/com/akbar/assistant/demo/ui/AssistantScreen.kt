package com.akbar.assistant.demo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akbar.assistant.demo.AppLanguage
import com.akbar.assistant.demo.AssistantState
import com.akbar.assistant.demo.AssistantUiState
import com.akbar.assistant.demo.ChatMessage
import com.akbar.assistant.demo.commands.AssistantCommand

private val Slate50 = Color(0xFFF8FAFC)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate400 = Color(0xFF94A3B8)
private val Slate500 = Color(0xFF64748B)
private val Slate700 = Color(0xFF334155)
private val Slate900 = Color(0xFF0F172A)
private val Accent = Color(0xFF475569)
private val AccentSoft = Color(0xFFE2E8F0)
private val UserBubble = Color(0xFF334155)
private val AssistantBubble = Color(0xFFFFFFFF)

@Composable
fun AkbarAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            background = Slate50,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Slate900,
            onSurface = Slate900
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
    onDraftChanged: (String) -> Unit,
    onSendText: () -> Unit,
    onRequestMicPermission: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Slate50, Slate100, Color(0xFFE8EEF5)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Header(state)
            Spacer(modifier = Modifier.height(12.dp))
            StatusCard(state, onRequestMicPermission)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "گفتگو",
                color = Slate700,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                if (state.chatMessages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.sessionActive) {
                                "دستور بگو یا بنویس — مثل ساعت یا هوا"
                            } else {
                                "بگو «اکبر» یا «هی اکبر» تا جلسه شروع شود"
                            },
                            color = Slate500,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.chatMessages, key = { it.id }) { message ->
                            ChatBubble(message)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            ChatInput(
                draft = state.draftText,
                enabled = state.permissionGranted,
                onDraftChanged = onDraftChanged,
                onSend = onSendText,
            )

            if (state.testModeVisible) {
                Spacer(modifier = Modifier.height(10.dp))
                TestPanel(onSimulateWake, onTestCommand)
            }

            Spacer(modifier = Modifier.height(8.dp))
            TestModeRow(enabled = state.testModeVisible, onToggle = onToggleTestMode)
        }
    }
}

@Composable
private fun Header(state: AssistantUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Akbar",
                color = Slate900,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
            Text(
                text = "دستیار صوتی دو‌زبانه",
                color = Slate500,
                fontSize = 13.sp,
            )
        }
        SessionBadge(active = state.sessionActive, state = state.state)
    }
}

@Composable
private fun SessionBadge(active: Boolean, state: AssistantState) {
    val bg = if (active) Color(0xFFDCFCE7) else Slate200
    val fg = if (active) Color(0xFF166534) else Slate700
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = when {
                active && state == AssistantState.LISTENING_COMMAND -> "جلسه · گوش می‌دهد"
                active -> "جلسه فعال"
                else -> "منتظر اکبر"
            },
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StatusCard(state: AssistantUiState, onRequestMicPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(phaseColor(state.state), CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = phaseLabel(state.state),
                    color = Slate900,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (state.language == AppLanguage.PERSIAN) "FA" else "EN",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.statusText,
                color = Slate700,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            if (state.hintText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.hintText,
                    color = Slate400,
                    fontSize = 12.sp,
                )
            }
            if (state.lastHeard.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "شنیدم: ${state.lastHeard}",
                    color = Slate500,
                    fontSize = 12.sp,
                )
            }
            if (!state.permissionGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestMicPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("اجازه میکروفون")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.fromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) UserBubble else AssistantBubble,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            shadowElevation = if (isUser) 0.dp else 1.dp,
            border = if (isUser) null else BorderStroke(1.dp, Slate200),
        ) {
            Text(
                text = message.text,
                color = if (isUser) Color.White else Slate900,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ChatInput(
    draft: String,
    enabled: Boolean,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChanged,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            placeholder = { Text("دستور بنویس…", color = Slate500) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = Slate200,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                cursorColor = Accent,
                focusedTextColor = Slate900,
                unfocusedTextColor = Slate900,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onSend,
            enabled = enabled && draft.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text("ارسال", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TestPanel(
    onSimulateWake: (AppLanguage) -> Unit,
    onTestCommand: (AssistantCommand) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AccentSoft.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "حالت تست",
                color = Slate700,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSimulateWake(AppLanguage.PERSIAN) },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("شبیه‌سازی اکبر")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onTestCommand(AssistantCommand.TellTime(AppLanguage.PERSIAN)) },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("ساعت")
                }
                OutlinedButton(
                    onClick = { onTestCommand(AssistantCommand.Weather(AppLanguage.PERSIAN)) },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("هوا")
                }
                OutlinedButton(
                    onClick = { onTestCommand(AssistantCommand.LightOn(AppLanguage.PERSIAN)) },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("چراغ")
                }
            }
        }
    }
}

@Composable
private fun TestModeRow(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("حالت تست", color = Slate500, fontSize = 13.sp)
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Slate200,
            ),
        )
    }
}

private fun phaseLabel(state: AssistantState): String = when (state) {
    AssistantState.IDLE -> "آماده"
    AssistantState.LISTENING_WAKE -> "منتظر کلمه اکبر"
    AssistantState.ACTIVATED -> "فعال شد"
    AssistantState.LISTENING_COMMAND -> "گوش به دستور"
    AssistantState.PROCESSING -> "در حال پاسخ"
    AssistantState.SPEAKING -> "در حال صحبت"
}

private fun phaseColor(state: AssistantState): Color = when (state) {
    AssistantState.IDLE -> Slate500
    AssistantState.LISTENING_WAKE -> Color(0xFF3B82F6)
    AssistantState.ACTIVATED -> Color(0xFF0EA5E9)
    AssistantState.LISTENING_COMMAND -> Color(0xFF16A34A)
    AssistantState.PROCESSING -> Color(0xFFCA8A04)
    AssistantState.SPEAKING -> Color(0xFF0F766E)
}
