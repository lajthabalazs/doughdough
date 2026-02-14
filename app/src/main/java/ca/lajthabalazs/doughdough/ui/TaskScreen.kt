package ca.lajthabalazs.doughdough.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.lajthabalazs.doughdough.alarm.AlarmScheduler
import ca.lajthabalazs.doughdough.data.RecipeStep
import ca.lajthabalazs.doughdough.notification.NotificationHelper
import ca.lajthabalazs.doughdough.recipe.RecipeSession
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
    // When waiting, we're waiting FOR the next step; when not, we show current step
    val isWaiting = session.nextAlarmAtMillis > System.currentTimeMillis()
    val nextStepIndex = session.currentStepIndex + 1
    val nextStep = session.recipe.steps.getOrNull(nextStepIndex)
    val step = if (isWaiting) null else session.currentStep
    if (step == null && !isWaiting) return  // No step and not waiting = shouldn't happen
    var remainingMillis by remember(session.nextAlarmAtMillis) {
        mutableLongStateOf((session.nextAlarmAtMillis - System.currentTimeMillis()).coerceAtLeast(0))
    }

    LaunchedEffect(session.nextAlarmAtMillis) {
        if (session.nextAlarmAtMillis > 0) {
            while (true) {
                val left = (session.nextAlarmAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
                remainingMillis = left
                if (left <= 0) {
                    onStepComplete()  // Refresh session (alarm fired, may have updated)
                    break
                }
                delay(1000)
            }
        }
    }

    NotificationHelper.ensureChannel(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = session.recipe.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (!isWaiting && step != null) {
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
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!isWaiting && step != null) {
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
        } else if (isWaiting && nextStep != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Next: ${nextStep.title}",
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
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        AlarmScheduler.cancelAlarm(context, nextStepIndex)
                        session.nextAlarmAtMillis = 0
                        session.setCurrentStep(nextStepIndex)
                        RecipeSession.save(context, session)
                        onStepComplete()
                    }
                ) {
                    Text("Start early")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = { showCancelConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel recipe", color = MaterialTheme.colorScheme.error)
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
                        if (isWaiting) AlarmScheduler.cancelAlarm(context, nextStepIndex)
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
    val totalSeconds = (millis / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        else -> "%02d:%02d".format(minutes, seconds)
    }
}
