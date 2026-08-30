package com.danielsalas.auto_music.utils

import android.content.Context
import android.util.Log
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

object YouTubeSolver {
    private const val TAG = "YouTubeSolver"
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    private val preprocessedCache = mutableMapOf<String, String>()

    suspend fun solve(context: Context, playerScriptUrl: String, nValue: String?, sValue: String?): Pair<String?, String?> = withContext(Dispatchers.IO) {
        if (nValue == null && sValue == null) return@withContext null to null
        
        try {
            val playerScript = fetchPlayerScript(playerScriptUrl) ?: return@withContext null to null
            val preprocessed = getPreprocessedPlayer(context, playerScript, playerScriptUrl) ?: return@withContext null to null
            
            val quickJs = QuickJs.create()
            try {
                quickJs.evaluate("_result = { n: null, sig: null };")
                quickJs.evaluate(preprocessed)
                
                var solvedN: String? = null
                if (nValue != null) {
                    solvedN = quickJs.evaluate("(_result.n)('$nValue')") as? String
                }
                
                var solvedSig: String? = null
                if (sValue != null) {
                    solvedSig = quickJs.evaluate("(_result.sig)('$sValue')") as? String
                }
                
                Log.d(TAG, "Solved: n=$solvedN, sig=$solvedSig")
                return@withContext solvedN to solvedSig
            } finally {
                quickJs.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Solver failed: ${e.message}")
            null to null
        }
    }

    private fun getPreprocessedPlayer(context: Context, playerScript: String, url: String): String? {
        val cacheKey = url.substringAfterLast("/")
        preprocessedCache[cacheKey]?.let { return it }

        return try {
            val meriyah = context.assets.open("solver/meriyah.js").bufferedReader().use { it.readText() }
            val astring = context.assets.open("solver/astring.js").bufferedReader().use { it.readText() }
            val solverCore = context.assets.open("solver/yt.solver.core.js").bufferedReader().use { it.readText() }

            val quickJs = QuickJs.create()
            try {
                quickJs.evaluate(meriyah)
                quickJs.evaluate(astring)
                quickJs.evaluate(solverCore)
                
                val input = buildJsonObject {
                    put("type", "player")
                    put("player", playerScript)
                    put("requests", buildJsonArray { })
                    put("output_preprocessed", true)
                }.toString()
                
                val result = quickJs.evaluate("JSON.stringify(jsc($input))") as? String
                if (result == null) return null
                
                val output = json.parseToJsonElement(result).jsonObject
                val preprocessed = output["preprocessed_player"]?.jsonPrimitive?.content
                if (preprocessed != null) {
                    preprocessedCache[cacheKey] = preprocessed
                }
                preprocessed
            } finally {
                quickJs.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Preprocessing failed: ${e.message}")
            null
        }
    }

    private fun fetchPlayerScript(url: String): String? {
        val fullUrl = if (url.startsWith("http")) url else "https://www.youtube.com$url"
        return try {
            val request = Request.Builder().url(fullUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
