package com.lajthabalazs.doughdough.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private val gson = Gson()
private val listType = object : TypeToken<List<SavedRecipeEntity>>() {}.type

/**
 * Domain model for UI: saved recipe record with parsed [recipe] content.
 */
data class SavedRecipeItem(
    val id: Long,
    val documentUrl: String,
    val fileName: String,
    val tabName: String,
    val downloadedAtMillis: Long,
    val lastUpdatedMillis: Long,
    val timesMade: Int,
    val recipe: Recipe
)

fun SavedRecipeEntity.toItem(): SavedRecipeItem = SavedRecipeItem(
    id = id,
    documentUrl = documentUrl,
    fileName = fileName,
    tabName = tabName,
    downloadedAtMillis = downloadedAtMillis,
    lastUpdatedMillis = lastUpdatedMillis,
    timesMade = timesMade,
    recipe = gson.fromJson(contentJson, Recipe::class.java)
)

fun recipeToContentJson(recipe: Recipe): String = gson.toJson(recipe)

/**
 * Persists and retrieves saved recipes in a JSON file. Coordinates with [SheetsRepository] for loading from sheets.
 */
class SavedRecipeRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "saved_recipes.json")
    private val mutex = Mutex()
    private val sheetsRepo = SheetsRepository(context.applicationContext)
    private val idGenerator = AtomicLong(System.currentTimeMillis())

    private suspend fun loadList(): MutableList<SavedRecipeEntity> {
        return mutex.withLock {
            if (!file.exists()) return@withLock mutableListOf()
            try {
                file.readText().let { json ->
                    if (json.isBlank()) mutableListOf()
                    else gson.fromJson<MutableList<SavedRecipeEntity>>(json, listType) ?: mutableListOf()
                }
            } catch (_: Exception) {
                mutableListOf()
            }
        }
    }

    private suspend fun saveList(list: List<SavedRecipeEntity>) {
        mutex.withLock {
            file.writeText(gson.toJson(list))
        }
    }

    private val _stateFlow = MutableStateFlow<List<SavedRecipeItem>>(emptyList())

    fun getAllFlow(): Flow<List<SavedRecipeItem>> = flow {
        val initial = loadList().map { it.toItem() }
        _stateFlow.value = initial
        emit(initial)
        _stateFlow.collect { emit(it) }
    }

    suspend fun getById(id: Long): SavedRecipeItem? =
        loadList().find { it.id == id }?.toItem()

    suspend fun deleteById(id: Long) {
        val list = loadList().filter { it.id != id }
        saveList(list)
        _stateFlow.value = list.map { it.toItem() }
    }

    suspend fun incrementTimesMade(id: Long) {
        val now = System.currentTimeMillis()
        val list = loadList().map { e ->
            if (e.id == id) e.copy(timesMade = e.timesMade + 1, lastUpdatedMillis = now) else e
        }
        saveList(list)
        _stateFlow.value = list.map { it.toItem() }
    }

    suspend fun touchLastUpdated(id: Long) {
        val now = System.currentTimeMillis()
        val list = loadList().map { e ->
            if (e.id == id) e.copy(lastUpdatedMillis = now) else e
        }
        saveList(list)
        _stateFlow.value = list.map { it.toItem() }
    }

    suspend fun insert(entity: SavedRecipeEntity): Long {
        val newId = idGenerator.incrementAndGet()
        val withId = entity.copy(id = newId)
        val list = loadList() + withId
        saveList(list)
        _stateFlow.value = list.map { it.toItem() }
        return newId
    }

    /**
     * Load spreadsheet from URL, then add or update saved recipes.
     * - If same documentUrl+tabName and same content: update lastUpdated only.
     * - If same documentUrl+tabName and different content: insert new record (keep old).
     * - If new documentUrl+tabName: insert new record.
     */
    suspend fun addOrUpdateFromLoad(documentUrl: String, result: SpreadsheetLoadResult): Result<Unit> {
        val now = System.currentTimeMillis()
        val normalizedUrl = documentUrl.trim()
        return try {
            var list: List<SavedRecipeEntity> = loadList()
            for (recipe in result.recipes) {
                val contentJson = recipeToContentJson(recipe)
                val existing = list.filter { it.documentUrl == normalizedUrl && it.tabName == recipe.name }
                val matching = existing.find { it.contentJson == contentJson }
                if (matching != null) {
                    list = list.map { if (it.id == matching.id) it.copy(lastUpdatedMillis = now) else it }
                } else {
                    list = list + SavedRecipeEntity(
                        id = idGenerator.incrementAndGet(),
                        documentUrl = normalizedUrl,
                        fileName = result.fileName,
                        tabName = recipe.name,
                        downloadedAtMillis = now,
                        lastUpdatedMillis = now,
                        timesMade = 0,
                        contentJson = contentJson
                    )
                }
            }
            saveList(list)
            _stateFlow.value = list.map { it.toItem() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Re-fetch a single tab from the sheet and add or update that recipe only.
     */
    suspend fun refreshRecipe(savedRecipeId: Long): Result<Unit> {
        val list = loadList()
        val existing = list.find { it.id == savedRecipeId } ?: return Result.failure(NoSuchElementException("Recipe not found"))
        val loadResult = sheetsRepo.loadSingleTab(existing.documentUrl, existing.tabName)
        loadResult.exceptionOrNull()?.let { return Result.failure(it) }
        val recipe = loadResult.getOrNull() ?: return Result.success(Unit)
        val now = System.currentTimeMillis()
        val contentJson = recipeToContentJson(recipe)
        val sameContent = existing.contentJson == contentJson
        val newList = if (sameContent) {
            list.map { if (it.id == savedRecipeId) it.copy(lastUpdatedMillis = now) else it }
        } else {
            list + SavedRecipeEntity(
                id = idGenerator.incrementAndGet(),
                documentUrl = existing.documentUrl,
                fileName = existing.fileName,
                tabName = existing.tabName,
                downloadedAtMillis = now,
                lastUpdatedMillis = now,
                timesMade = 0,
                contentJson = contentJson
            )
        }
        saveList(newList)
        _stateFlow.value = newList.map { it.toItem() }
        return Result.success(Unit)
    }

    suspend fun loadFromSheet(url: String): Result<SpreadsheetLoadResult> =
        sheetsRepo.loadRecipes(url)
}
