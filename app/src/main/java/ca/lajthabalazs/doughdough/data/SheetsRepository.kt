package ca.lajthabalazs.doughdough.data

import android.util.Log
import ca.lajthabalazs.doughdough.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fetches recipes from Google Sheets.
 * Uses Sheets API v4 when API key is available (for multiple tabs).
 * Falls back to gviz/CSV for public sheets when no API key (single sheet, gid=0).
 */
class SheetsRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val defaultSpreadsheetId = "1B_gaW3csiWVCZG3FGsQiARSNZ_w_OPh1QZbwWWo-FNY"

    /**
     * Extract spreadsheet ID from URL or use as-is if it's already an ID.
     */
    fun extractSpreadsheetId(urlOrId: String): String {
        val trimmed = urlOrId.trim()
        val match = Regex("""/d/([a-zA-Z0-9_-]+)""").find(trimmed)
        return match?.groupValues?.get(1) ?: trimmed
    }

    suspend fun loadRecipes(spreadsheetUrlOrId: String): Result<List<Recipe>> = withContext(Dispatchers.IO) {
        val spreadsheetId = extractSpreadsheetId(spreadsheetUrlOrId.ifBlank { defaultSpreadsheetId })
        val apiKey = BuildConfig.GOOGLE_SHEETS_API_KEY

        if (apiKey.isNotEmpty()) {
            Log.d(TAG, "Google Sheets API key is set (length=${apiKey.length}), using Sheets API v4")
            loadWithSheetsApi(spreadsheetId, apiKey)
        } else {
            Log.d(TAG, "No Google Sheets API key configured, using CSV export fallback (single sheet, gid=0)")
            loadWithGviz(spreadsheetId)
        }
    }

    companion object {
        private const val TAG = "SheetsRepository"
    }

    private fun loadWithSheetsApi(spreadsheetId: String, apiKey: String): Result<List<Recipe>> {
        return try {
            Log.d(TAG, "Sheets API: fetching spreadsheet metadata for id=$spreadsheetId")
            // Fetch spreadsheet metadata (sheet names)
            val metadataUrl = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?key=$apiKey&fields=sheets.properties"
            val metadataReq = Request.Builder().url(metadataUrl).build()
            val metadataRes = client.newCall(metadataReq).execute()
            if (!metadataRes.isSuccessful) {
                val body = metadataRes.body?.string() ?: ""
                Log.e(TAG, "Sheets API key rejected or request failed: code=${metadataRes.code}, body=$body")
                return Result.failure(Exception("Sheets API error: ${metadataRes.code} - $body"))
            }
            Log.d(TAG, "Sheets API: key accepted, metadata received successfully")
            val metadata = JSONObject(metadataRes.body!!.string())
            val sheets = metadata.getJSONArray("sheets")
            Log.d(TAG, "Sheets API: found ${sheets.length()} sheet(s)")
            val recipes = mutableListOf<Recipe>()
            for (i in 0 until sheets.length()) {
                val sheet = sheets.getJSONObject(i)
                val props = sheet.getJSONObject("properties")
                val sheetId = props.getLong("sheetId")
                val title = props.optString("title", "Sheet${i + 1}")
                val recipe = loadSheetData(spreadsheetId, apiKey, title, sheetId)
                if (recipe != null && recipe.steps.isNotEmpty()) {
                    recipes.add(recipe)
                    Log.d(TAG, "Sheets API: loaded recipe \"$title\" (${recipe.steps.size} steps)")
                }
            }
            if (recipes.isEmpty()) {
                Log.d(TAG, "Sheets API: no recipes with steps, falling back to CSV for first sheet")
            }
            Result.success(recipes.ifEmpty { listOf(parseCsvToRecipe(loadCsv(spreadsheetId, 0), "Default")) })
        } catch (e: Exception) {
            Log.e(TAG, "Sheets API failed, falling back to CSV", e)
            loadWithGviz(spreadsheetId)
        }
    }

    private fun loadSheetData(spreadsheetId: String, apiKey: String, sheetName: String, sheetId: Long): Recipe? {
        val escapedName = sheetName.replace("'", "''")
        val range = "'$escapedName'!A:C"
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${java.net.URLEncoder.encode(range, "UTF-8").replace("+", "%20")}?key=$apiKey"
        return try {
            val req = Request.Builder().url(url).build()
            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return null
            val json = JSONObject(res.body!!.string())
            val values = json.optJSONArray("values") ?: return null
            parseValuesToRecipe(values, sheetName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sheet $sheetName", e)
            null
        }
    }

    private fun loadWithGviz(spreadsheetId: String): Result<List<Recipe>> {
        return try {
            Log.d(TAG, "CSV fallback: loading first sheet (gid=0) for id=$spreadsheetId")
            val csv = loadCsv(spreadsheetId, 0)
            val recipe = parseCsvToRecipe(csv, "Recipe")
            Log.d(TAG, "CSV fallback: loaded recipe \"${recipe.name}\" (${recipe.steps.size} steps)")
            Result.success(listOf(recipe))
        } catch (e: Exception) {
            Log.e(TAG, "CSV fallback: load failed", e)
            Result.failure(e)
        }
    }

    private fun loadCsv(spreadsheetId: String, gid: Long): String {
        val url = "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=csv&gid=$gid"
        val req = Request.Builder().url(url).build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) throw Exception("Failed to fetch CSV: ${res.code}")
        return res.body!!.string()
    }

    private fun parseCsvToRecipe(csv: String, defaultName: String): Recipe {
        val rows = parseCsv(csv)
        return parseRowsToRecipe(rows, defaultName)
    }

    private fun parseCsv(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var inQuotes = false
        var cell = StringBuilder()
        for (i in csv.indices) {
            val c = csv[i]
            when {
                c == '"' -> inQuotes = !inQuotes
                inQuotes -> cell.append(c)
                c == ',' -> {
                    current.add(cell.toString().trim())
                    cell = StringBuilder()
                }
                c == '\n' || c == '\r' -> {
                    if (c == '\n' || (c == '\r' && (i + 1 >= csv.length || csv[i + 1] != '\n'))) {
                        current.add(cell.toString().trim())
                        cell = StringBuilder()
                        if (current.any { it.isNotEmpty() }) rows.add(current)
                        current = mutableListOf()
                    }
                }
                else -> cell.append(c)
            }
        }
        if (cell.isNotEmpty() || current.isNotEmpty()) {
            current.add(cell.toString().trim())
            if (current.any { it.isNotEmpty() }) rows.add(current)
        }
        return rows
    }

    private fun parseValuesToRecipe(values: org.json.JSONArray, name: String): Recipe? {
        val rows = mutableListOf<List<String>>()
        for (i in 0 until values.length()) {
            val row = values.getJSONArray(i)
            val cells = mutableListOf<String>()
            for (j in 0 until row.length()) {
                cells.add(row.optString(j, ""))
            }
            rows.add(cells)
        }
        return parseRowsToRecipe(rows, name)
    }

    private fun parseRowsToRecipe(rows: List<List<String>>, name: String): Recipe {
        if (rows.isEmpty()) return Recipe(id = name, name = name, steps = emptyList())
        val dataRows = if (rows.size > 1) {
            val first = rows[0].map { it.lowercase().trim() }
            if (first.getOrNull(0)?.contains("start") == true || first.getOrNull(1)?.contains("title") == true) {
                rows.drop(1)
            } else rows
        } else rows

        val steps = mutableListOf<RecipeStep>()
        var previousCumulativeMs = 0L
        for (row in dataRows) {
            val startTime = row.getOrNull(0)?.trim() ?: ""
            val title = row.getOrNull(1)?.trim() ?: row.getOrNull(0)?.trim() ?: ""
            val description = row.getOrNull(2)?.trim() ?: ""
            if (title.isBlank() && description.isBlank()) continue
            val (step, nextCumulative) = TimeParser.parseStep(startTime, title, description, previousCumulativeMs)
            steps.add(step)
            previousCumulativeMs = nextCumulative
        }
        return Recipe(id = name, name = name, steps = steps)
    }
}
