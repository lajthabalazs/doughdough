package com.lajthabalazs.doughdough.ui

import android.content.res.Configuration
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lajthabalazs.doughdough.alarm.AlarmScheduler
import com.lajthabalazs.doughdough.alarm.AlarmSoundManager
import com.lajthabalazs.doughdough.data.RecipeStep
import com.lajthabalazs.doughdough.notification.NotificationHelper
import com.lajthabalazs.doughdough.recipe.RecipeSession
import kotlinx.coroutines.delay

@Composable
fun TaskScreen(
    session: RecipeSession,
    onStepComplete: () -> Unit,
    onAllComplete: () -> Unit,
    onRecipeCancelled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCancelConfirm by remember { mutableStateOf(false) }
    // When waiting, we're waiting FOR the next step; when alarm has rung we stay on timer and count negative
    val isWaiting = session.nextAlarmAtMillis > System.currentTimeMillis()
    val nextStepIndex = session.currentStepIndex + 1
    val nextStep = session.recipe.steps.getOrNull(nextStepIndex)
    val showTimerView = nextStep != null && session.nextAlarmAtMillis != 0L
    val step = if (showTimerView) null else session.currentStep
    if (step == null && !showTimerView) return  // No step and not showing timer = shouldn't happen
    var remainingMillis by remember(session.nextAlarmAtMillis) {
        mutableLongStateOf(session.nextAlarmAtMillis - System.currentTimeMillis())
    }

    LaunchedEffect(session.nextAlarmAtMillis) {
        if (session.nextAlarmAtMillis != 0L) {
            while (true) {
                remainingMillis = session.nextAlarmAtMillis - System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    NotificationHelper.ensureChannel(context)

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val taskContentScroll = rememberScrollState()

    @Composable
    fun TaskContent() {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = session.recipe.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!showTimerView && step != null) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = if (isLandscape) Modifier else Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            } else if (showTimerView) {
                Text(
                    text = "Next: ${nextStep!!.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatRemainingTime(remainingMillis),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    @Composable
    fun ActionButtons() {
        Column(
            modifier = if (isLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxWidth(),
            verticalArrangement = if (isLandscape) Arrangement.SpaceBetween else Arrangement.Top
        ) {
            Column(modifier = Modifier) {
                if (isLandscape && !showTimerView && step != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = session.recipe.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                if (!showTimerView && step != null) {
                    if (session.currentStepIndex > 0) {
                        TextButton(
                            onClick = {
                                session.setCurrentStep(session.currentStepIndex - 1)
                                session.nextAlarmAtMillis = 0
                                RecipeSession.save(context, session)
                                onStepComplete()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Go back")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            if (session.hasNextStep) {
                                scheduleNextAndWait(context, session, onStepComplete)
                            } else {
                                RecipeSession.clear(context)
                                onAllComplete()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(
                            text = if (session.hasNextStep) "Done" else "Finish recipe",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else if (showTimerView) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TextButton(
                            onClick = {
                                AlarmSoundManager.stop()
                                AlarmScheduler.cancelAlarm(context, nextStepIndex)
                                session.setCurrentStep((session.currentStepIndex - 1).coerceAtLeast(0))
                                session.nextAlarmAtMillis = 0
                                RecipeSession.save(context, session)
                                onStepComplete()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Go back")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                AlarmSoundManager.stop()
                                AlarmScheduler.cancelAlarm(context, nextStepIndex)
                                session.nextAlarmAtMillis = 0
                                session.setCurrentStep(nextStepIndex)
                                RecipeSession.save(context, session)
                                onStepComplete()
                            }
                        ) {
                            Text("Start ${nextStep!!.title}")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                val newTrigger = if (session.nextAlarmAtMillis + 60_000 < System.currentTimeMillis()) {
                                    System.currentTimeMillis() + 60_000
                                } else {
                                    session.nextAlarmAtMillis + 60_000
                                }
                                AlarmSoundManager.stop()
                                AlarmScheduler.scheduleAlarm(context, newTrigger, nextStepIndex)
                                session.nextAlarmAtMillis = newTrigger
                                RecipeSession.save(context, session)
                                onStepComplete()
                            }
                        ) {
                            Text("Add 1 minute")
                        }
                    }
                }
            }
            Spacer(modifier = if (isLandscape) Modifier.height(0.dp) else Modifier.height(16.dp))
            TextButton(
                onClick = { showCancelConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel recipe", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(taskContentScroll)
            ) {
                    if (!showTimerView && step != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(20.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = step.description,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    } else if (showTimerView) {
                        Text(
                            text = "Next: ${nextStep!!.title}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatRemainingTime(remainingMillis),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
            }
            Spacer(modifier = Modifier.width(24.dp))
            Column(
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButtons()
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            TaskContent()
            Spacer(modifier = Modifier.height(24.dp))
            ActionButtons()
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel recipe?") },
            text = { Text("Do you want to cancel the recipe?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        AlarmSoundManager.stop()
                        if (showTimerView) AlarmScheduler.cancelAlarm(context, nextStepIndex)
                        RecipeSession.clear(context)
                        onRecipeCancelled()
                    }
                ) {
                    Text("Cancel recipe", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Continue recipe")
                }
            }
        )
    }
}

private fun scheduleNextAndWait(
    context: Context,
    session: RecipeSession,
    onStepComplete: () -> Unit
) {
    val nextIndex = session.currentStepIndex + 1
    val nextStep = session.recipe.steps.getOrNull(nextIndex) ?: return

    if (nextStep.durationMillis > 0) {
        val triggerAt = System.currentTimeMillis() + nextStep.durationMillis
        AlarmScheduler.scheduleAlarm(context, triggerAt, nextIndex)
        session.nextAlarmAtMillis = triggerAt
    } else {
        // No time on this step: show it immediately after Done
        session.setCurrentStep(nextIndex)
        session.nextAlarmAtMillis = 0
    }
    RecipeSession.save(context, session)
    onStepComplete()
}

private fun formatRemainingTime(millis: Long): String {
    val totalSeconds = kotlin.math.abs(millis / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val sign = if (millis < 0) "-" else ""
    return when {
        hours > 0 -> "$sign%d:%02d:%02d".format(hours, minutes, seconds)
        else -> "$sign%02d:%02d".format(minutes, seconds)
    }
}
