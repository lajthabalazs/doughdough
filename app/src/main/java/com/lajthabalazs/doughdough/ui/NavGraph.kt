package com.lajthabalazs.doughdough.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lajthabalazs.doughdough.data.Recipe
import com.lajthabalazs.doughdough.data.SavedRecipeRepository
import com.lajthabalazs.doughdough.recipe.RecipeSession
import kotlinx.coroutines.launch

private const val SELECTOR = "selector"
private const val STEPS = "steps"
private const val TASK = "task"
private const val WELL_DONE = "welldone"

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedRepo = remember(ctx) { SavedRecipeRepository(ctx.applicationContext) }
    var selectedRecipe by remember { mutableStateOf<Recipe?>(null) }
    var selectedSavedRecipeId by remember { mutableStateOf<Long?>(null) }
    val pendingOpenTask = AppState.pendingOpenTaskStep

    // When app opens with a recipe in progress, go straight to the task screen
    LaunchedEffect(Unit) {
        if (RecipeSession.restore(ctx) != null) {
            navController.navigate(TASK) {
                popUpTo(SELECTOR) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(pendingOpenTask) {
        if (pendingOpenTask != null) {
            AppState.pendingOpenTaskStep = null
            navController.navigate(TASK) {
                popUpTo(SELECTOR) { inclusive = false }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = SELECTOR
    ) {
        composable(SELECTOR) {
            RecipeSelectorScreen(
                onRecipeSelected = { recipe, savedRecipeId ->
                    selectedRecipe = recipe
                    selectedSavedRecipeId = savedRecipeId
                    navController.navigate(STEPS)
                }
            )
        }
        composable(STEPS) {
            selectedRecipe?.let { recipe ->
                RecipeStepsScreen(
                    recipe = recipe,
                    onBack = { navController.popBackStack() },
                    onStart = {
                        selectedSavedRecipeId?.let { id ->
                            scope.launch { savedRepo.incrementTimesMade(id) }
                        }
                        val context = navController.context
                        val newSession = RecipeSession(recipe, 0)
                        RecipeSession.save(context, newSession)
                        navController.navigate(TASK) {
                            popUpTo(SELECTOR) { inclusive = false }
                        }
                    }
                )
            }
        }
        composable(TASK) {
            val ctx = LocalContext.current
            var sessionRefresh by remember { mutableStateOf(0) }
            val currentSession = remember(sessionRefresh) { RecipeSession.restore(ctx) }
            if (currentSession != null) {
                TaskScreen(
                    session = currentSession,
                    onStepComplete = { sessionRefresh++ },
                    onAllComplete = {
                        AppState.completedRecipeName = currentSession.recipe.name
                        scope.launch {
                            AppState.completedRecipeTimesMade = selectedSavedRecipeId
                                ?.let { savedRepo.getById(it)?.timesMade }
                            navController.navigate(WELL_DONE) {
                                popUpTo(SELECTOR) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onRecipeCancelled = {
                        navController.navigate(SELECTOR) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
        composable(WELL_DONE) {
            val recipeName = AppState.completedRecipeName ?: "Recipe"
            val timesMade = AppState.completedRecipeTimesMade
            WellDoneScreen(
                recipeName = recipeName,
                timesMade = timesMade,
                onContinue = {
                    navController.navigate(SELECTOR) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
