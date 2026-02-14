package ca.lajthabalazs.doughdough.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppState {
    var completedRecipeName: String? = null
    /** When non-null, NavGraph should navigate to Task screen (e.g. from notification tap) */
    var pendingOpenTaskStep by mutableStateOf<Int?>(null)
}
