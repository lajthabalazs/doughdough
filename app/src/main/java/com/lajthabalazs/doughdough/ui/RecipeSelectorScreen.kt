package com.lajthabalazs.doughdough.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lajthabalazs.doughdough.data.Recipe
import com.lajthabalazs.doughdough.data.SavedRecipeItem
import com.lajthabalazs.doughdough.data.SavedRecipeRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecipeSelectorScreen(
    onRecipeSelected: (Recipe, savedRecipeId: Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedRepo = remember(context) { SavedRecipeRepository(context.applicationContext) }
    val savedRecipes by savedRepo.getAllFlow().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogUrl by remember { mutableStateOf("") }
    var addDialogLoading by remember { mutableStateOf(false) }
    var addDialogError by remember { mutableStateOf<String?>(null) }
    var deleteConfirmItem by remember { mutableStateOf<SavedRecipeItem?>(null) }
    var refreshingId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "DoughDough",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Recipe alarm assistant",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showAddDialog = true; addDialogUrl = ""; addDialogError = null },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("Add from Google Sheet")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Recipes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (savedRecipes.isEmpty()) {
            Text(
                text = "Tap \"Add from Google Sheet\" to add recipes. Make sure the sheet is shared so anyone with the link can view it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(savedRecipes) { item ->
                    SavedRecipeCard(
                        item = item,
                        isRefreshing = refreshingId == item.id,
                        onClick = { onRecipeSelected(item.recipe, item.id) },
                        onRefresh = {
                            refreshingId = item.id
                            scope.launch {
                                savedRepo.refreshRecipe(item.id)
                                refreshingId = null
                            }
                        },
                        onDelete = { deleteConfirmItem = item }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { if (!addDialogLoading) showAddDialog = false },
            title = { Text("Add from Google Sheet") },
            text = {
                Column {
                    Text(
                        text = "Make sure the sheet is shared so anyone with the link can view it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "You can use this sheet as a reference on how to write your recipes:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "https://docs.google.com/spreadsheets/d/1B_gaW3csiWVCZG3FGsQiARSNZ_w_OPh1QZbwWWo-FNY/edit?usp=sharing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = addDialogUrl,
                        onValueChange = { addDialogUrl = it; addDialogError = null },
                        label = { Text("Sheet URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        isError = addDialogError != null
                    )
                    addDialogError?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        addDialogLoading = true
                        addDialogError = null
                        scope.launch {
                            val url = addDialogUrl.trim()
                            if (url.isBlank()) {
                                addDialogError = "Enter a URL"
                                addDialogLoading = false
                                return@launch
                            }
                            savedRepo.loadFromSheet(url)
                                .onSuccess { result ->
                                    if (result.recipes.isEmpty()) {
                                        addDialogError = "No recipes found"
                                    } else {
                                        savedRepo.addOrUpdateFromLoad(url, result)
                                        showAddDialog = false
                                    }
                                }
                                .onFailure { e ->
                                    addDialogError = e.message ?: "Failed to load"
                                }
                            addDialogLoading = false
                        }
                    },
                    enabled = !addDialogLoading
                ) {
                    if (addDialogLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Load")
                    }
                }
            },
            dismissButton = {
                Button(onClick = { showAddDialog = false }, enabled = !addDialogLoading) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteConfirmItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteConfirmItem = null },
            title = { Text("Delete recipe?") },
            text = { Text("Remove \"${item.tabName}\" from your list? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            savedRepo.deleteById(item.id)
                            deleteConfirmItem = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { deleteConfirmItem = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SavedRecipeCard(
    item: SavedRecipeItem,
    isRefreshing: Boolean,
    onClick: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.tabName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " · Made ${item.timesMade}  times",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Downloaded ${dateFormat.format(Date(item.downloadedAtMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.size(40.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh from sheet")
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
