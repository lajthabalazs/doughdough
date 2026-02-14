package com.lajthabalazs.doughdough

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.lajthabalazs.doughdough.ui.theme.DoughDoughTheme
import com.lajthabalazs.doughdough.ui.NavGraph

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TASK_STEP = "open_task_step"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        checkIntentForTaskStep(intent)
        setContent {
            DoughDoughTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIntentForTaskStep(intent)
    }

    private fun checkIntentForTaskStep(intent: Intent?) {
        val step = intent?.getIntExtra(EXTRA_OPEN_TASK_STEP, -1)?.takeIf { it >= 0 }
        if (step != null) {
            com.lajthabalazs.doughdough.ui.AppState.pendingOpenTaskStep = step
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {}
                else -> requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }
}
