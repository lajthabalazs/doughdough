package com.lajthabalazs.doughdough.recipe

import android.content.Context
import com.lajthabalazs.doughdough.data.Recipe
import com.lajthabalazs.doughdough.data.RecipeStep
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tracks the current state of an active recipe session.
 */
data class RecipeSession(
    val recipe: Recipe,
    var currentStepIndex: Int,
    var nextAlarmAtMillis: Long = 0
) {
    val currentStep: RecipeStep? get() = recipe.steps.getOrNull(currentStepIndex)
    val isLastStep: Boolean get() = currentStepIndex >= recipe.steps.lastIndex
    val hasNextStep: Boolean get() = currentStepIndex < recipe.steps.lastIndex
    val nextStep: RecipeStep? get() = recipe.steps.getOrNull(currentStepIndex + 1)

    fun setCurrentStep(index: Int) { currentStepIndex = index }

    fun toJson(): JSONObject = JSONObject().apply {
        put("recipeId", recipe.id)
        put("recipeName", recipe.name)
        put("steps", JSONArray().apply {
            recipe.steps.forEach { step ->
                put(JSONObject().apply {
                    put("startTime", step.startTime)
                    put("title", step.title)
                    put("description", step.description)
                    put("durationMillis", step.durationMillis)
                })
            }
        })
        put("currentStepIndex", currentStepIndex)
        put("nextAlarmAtMillis", nextAlarmAtMillis)
    }

    companion object {
        private const val PREFS = "recipe_session"
        private const val KEY = "session"

        fun save(context: Context, session: RecipeSession) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, session.toJson().toString())
                .apply()
        }

        fun restore(context: Context): RecipeSession? {
            val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: return null
            return try {
                val obj = JSONObject(json)
                val stepsArray = obj.getJSONArray("steps")
                val steps = (0 until stepsArray.length()).map { i ->
                    val s = stepsArray.getJSONObject(i)
                    RecipeStep(
                        startTime = s.getString("startTime"),
                        title = s.getString("title"),
                        description = s.getString("description"),
                        durationMillis = s.getLong("durationMillis")
                    )
                }
                var session = RecipeSession(
                    recipe = Recipe(
                        id = obj.getString("recipeId"),
                        name = obj.getString("recipeName"),
                        steps = steps
                    ),
                    currentStepIndex = obj.getInt("currentStepIndex"),
                    nextAlarmAtMillis = obj.optLong("nextAlarmAtMillis", 0)
                )
                // If we were waiting but the alarm time has passed (e.g. app was killed), advance to the next step
                if (session.nextAlarmAtMillis > 0 && session.nextAlarmAtMillis <= System.currentTimeMillis()) {
                    val nextIndex = session.currentStepIndex + 1
                    if (nextIndex < steps.size) {
                        session = session.copy(
                            currentStepIndex = nextIndex,
                            nextAlarmAtMillis = 0
                        )
                        save(context, session)
                    } else {
                        session = session.copy(nextAlarmAtMillis = 0)
                        save(context, session)
                    }
                }
                session
            } catch (e: Exception) {
                null
            }
        }

        fun clear(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        }
    }
}
