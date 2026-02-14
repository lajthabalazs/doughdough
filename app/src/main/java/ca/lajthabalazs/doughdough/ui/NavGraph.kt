package ca.lajthabalazs.doughdough.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ca.lajthabalazs.doughdough.data.Recipe
import ca.lajthabalazs.doughdough.recipe.RecipeSession

private const val SELECTOR = "selector"
private const val STEPS = "steps"
private const val TASK = "task"
private const val WELL_DONE = "welldone"

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    val ctx = LocalContext.current
    var selectedRecipe by remember { mutableStateOf<Recipe?>(null) }
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
                onRecipeSelected = { recipe ->
                    selectedRecipe = recipe
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
                        val ctx = navController.context
                        val newSession = RecipeSession(recipe, 0)
                        RecipeSession.save(ctx, newSession)
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
                        navController.navigate(WELL_DONE) {
                            popUpTo(SELECTOR) { inclusive = true }
                            launchSingleTop = true
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
            WellDoneScreen(
                recipeName = recipeName,
                onContinue = {
                    navController.navigate(SELECTOR) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
