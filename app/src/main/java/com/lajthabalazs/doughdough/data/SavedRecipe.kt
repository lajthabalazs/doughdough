package com.lajthabalazs.doughdough.data

/**
 * Persisted recipe record: metadata + stored content as JSON.
 */
data class SavedRecipeEntity(
    val id: Long,
    val documentUrl: String,
    val fileName: String,
    val tabName: String,
    val downloadedAtMillis: Long,
    val lastUpdatedMillis: Long,
    val timesMade: Int,
    val contentJson: String
)
