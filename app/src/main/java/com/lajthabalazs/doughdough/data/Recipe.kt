package com.lajthabalazs.doughdough.data

/**
 * Represents a recipe with its steps.
 * @param id Unique identifier (e.g. sheet name or gid)
 * @param name Display name of the recipe
 * @param steps Ordered list of recipe steps
 */
data class Recipe(
    val id: String,
    val name: String,
    val steps: List<RecipeStep>
)

/**
 * A single step in a recipe with timing information.
 * @param startTime Raw timing string: absolute (e.g. "16:00") or relative (e.g. "+16h", "+30m")
 * @param title Step title
 * @param description Detailed instructions
 * @param durationMillis Parsed duration in milliseconds (for relative steps from previous)
 */
data class RecipeStep(
    val startTime: String,
    val title: String,
    val description: String,
    val durationMillis: Long
)
