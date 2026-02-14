package com.lajthabalazs.doughdough.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.lajthabalazs.doughdough.BuildConfig
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Fetches recipes from Google Sheets.
 * When API key is present: uses the official Google Sheets API Android client (so key can be
 * restricted to this app). When no key: falls back to HTTP CSV export (single sheet, gid=0).
 */
class SheetsRepository(private val context: Context) {
    private val httpClient = OkHttpClient.Builder()
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
            Log.d(TAG, "Google Sheets API key is set, using Sheets API v4 (Android client)")
            loadWithSheetsApi(spreadsheetId, apiKey)
        } else {
            Log.d(TAG, "No Google Sheets API key configured, using HTTP CSV fallback (single sheet, gid=0)")
            loadWithHttpCsv(spreadsheetId)
        }
    }

    companion object {
        private const val TAG = "SheetsRepository"
    }

    private fun buildSheetsService(apiKey: String): Sheets? {
        val packageName = context.packageName
        val sha1 = getSigningCertificateSha1(context)
        if (sha1 == null) {
            Log.w(TAG, "Could not get signing certificate SHA-1; Android app restriction may fail")
        }
        val requestInitializer = HttpRequestInitializer { request ->
            request.url.set("key", apiKey)
            request.headers.set("X-Android-Package", packageName)
            if (sha1 != null) {
                request.headers.set("X-Android-Cert", sha1)
            }
        }
        return Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            requestInitializer
        )
            .setApplicationName("DoughDough")
            .build()
    }

    private fun loadWithSheetsApi(spreadsheetId: String, apiKey: String): Result<List<Recipe>> {
        return try {
            val sheets = buildSheetsService(apiKey) ?: return loadWithHttpCsv(spreadsheetId)
            Log.d(TAG, "Sheets API: fetching spreadsheet metadata for id=$spreadsheetId")
            val spreadsheet = sheets.spreadsheets()
                .get(spreadsheetId)
                .setFields("sheets.properties")
                .execute()
            val sheetList = spreadsheet.sheets ?: emptyList()
            Log.d(TAG, "Sheets API: found ${sheetList.size} sheet(s)")
            val recipes = mutableListOf<Recipe>()
            for (sheet in sheetList) {
                val props = sheet.properties ?: continue
                val title = props.title ?: "Sheet${recipes.size + 1}"
                val recipe = loadSheetDataWithClient(sheets, spreadsheetId, title)
                if (recipe != null && recipe.steps.isNotEmpty()) {
                    recipes.add(recipe)
                    Log.d(TAG, "Sheets API: loaded recipe \"$title\" (${recipe.steps.size} steps)")
                }
            }
            if (recipes.isEmpty()) {
                Log.d(TAG, "Sheets API: no recipes with steps, falling back to CSV for first sheet")
            }
            Result.success(
                recipes.ifEmpty { listOf(parseCsvToRecipe(loadCsvViaHttp(spreadsheetId, 0), "Default")) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sheets API failed, falling back to HTTP CSV", e)
            loadWithHttpCsv(spreadsheetId)
        }
    }

    private fun loadSheetDataWithClient(
        sheets: Sheets,
        spreadsheetId: String,
        sheetName: String
    ): Recipe? {
        return try {
            val escapedName = sheetName.replace("'", "''")
            val range = "'$escapedName'!A:C"
            val response = sheets.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute()
            val values = response.getValues() ?: return null
            val rows = values.map { row -> row.map { it?.toString() ?: "" } }
            parseRowsToRecipe(rows, sheetName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sheet $sheetName", e)
            null
        }
    }

    private fun loadWithHttpCsv(spreadsheetId: String): Result<List<Recipe>> {
        return try {
            Log.d(TAG, "CSV fallback: loading first sheet (gid=0) for id=$spreadsheetId")
            val csv = loadCsvViaHttp(spreadsheetId, 0)
            val recipe = parseCsvToRecipe(csv, "Recipe")
            Log.d(TAG, "CSV fallback: loaded recipe \"${recipe.name}\" (${recipe.steps.size} steps)")
            Result.success(listOf(recipe))
        } catch (e: Exception) {
            Log.e(TAG, "CSV fallback: load failed", e)
            Result.failure(e)
        }
    }

    private fun loadCsvViaHttp(spreadsheetId: String, gid: Long): String {
        val url = "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=csv&gid=$gid"
        val req = Request.Builder().url(url).build()
        val res = httpClient.newCall(req).execute()
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

/**
 * Returns the SHA-1 fingerprint of the app's signing certificate (hex, no colons),
 * or null if unavailable. Used for X-Android-Cert when restricting the API key to this Android app.
 */
private fun getSigningCertificateSha1(context: Context): String? {
    return try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            packageInfo.signatures
        } ?: return null
        val sig = signatures.firstOrNull() ?: return null
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(sig.toByteArray())
        digest.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.e("SheetsRepository", "Failed to get signing cert SHA-1", e)
        null
    }
}
