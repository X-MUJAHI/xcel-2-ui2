package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object PanelFileManager {

    private const val TAG = "PanelFileManager"
    private const val PREFS_NAME = "xcel_prefs"
    const val KEY_MOD_STATE = "mod_power_state" // "ON" or "OFF"
    const val KEY_FILE1_NAME = "panel_file1_name"
    const val KEY_FILE2_NAME = "panel_file2_name"
    const val KEY_TARGET_PATH_1 = "cached_target_path_1"
    const val KEY_TARGET_PATH_2 = "cached_target_path_2"
    const val KEY_FILES_ACTIVATED = "panel_files_activated"

    const val KEY_ACTIVE_SCRIPT_ID = "active_script_id"
    const val KEY_ACTIVE_SCRIPT_NAME = "active_script_name"
    const val KEY_CACHED_SCRIPTS_COUNT = "cached_scripts_count"

    const val VERCEL_TARGET_1_URL = "https://xcel2.vercel.app/targetpath1.txt"
    const val VERCEL_TARGET_2_URL = "https://xcel2.vercel.app/targetpath2.txt"

    const val DEFAULT_TARGET_PATH_1 = "/storage/emulated/0/Download/"
    const val DEFAULT_TARGET_PATH_2 = "/storage/emulated/0/xcel1/"

    var lastLog: String = ""

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    data class ScriptItem(
        val id: Int,
        val name: String,
        val turnOnFile1Url: String,
        val turnOnFile2Url: String,
        val file1Name: String,
        val file2Name: String,
        val rawImageResName: String = "script$id"
    )

    data class ScriptsConfig(
        val scripts: List<ScriptItem>,
        val turnOffFile1Url: String,
        val turnOffFile2Url: String,
        val file1Name: String,
        val file2Name: String,
        val targetPath1Override: String? = null,
        val targetPath2Override: String? = null
    )

    data class FileManifest(
        val turnOnFile1Url: String,
        val turnOnFile2Url: String,
        val turnOffFile1Url: String,
        val turnOffFile2Url: String,
        val file1Name: String,
        val file2Name: String,
        val targetPath1Override: String? = null,
        val targetPath2Override: String? = null
    )

    /**
     * Staging directory located inside the app's package folder in Android:
     * /storage/emulated/0/Android/data/<package_name>/files/panel_staged_files/
     */
    fun getStagedBaseDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "panel_staged_files")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getScriptsBaseDir(context: Context): File {
        val dir = File(getStagedBaseDir(context), "scripts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getScriptStagedDir(context: Context, scriptId: Int): File {
        val dir = File(getScriptsBaseDir(context), "script_$scriptId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTurnOnDir(context: Context): File {
        val dir = File(getStagedBaseDir(context), "turn_on")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTurnOffDir(context: Context): File {
        val dir = File(getStagedBaseDir(context), "turn_off")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getActiveScriptId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ACTIVE_SCRIPT_ID, 1)
    }

    fun setActiveScript(context: Context, scriptId: Int, scriptName: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putInt(KEY_ACTIVE_SCRIPT_ID, scriptId)
        if (!scriptName.isNullOrBlank()) {
            editor.putString(KEY_ACTIVE_SCRIPT_NAME, scriptName)
        }
        editor.apply()
    }

    fun getActiveScriptName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = getActiveScriptId(context)
        return prefs.getString(KEY_ACTIVE_SCRIPT_NAME, "Script $id") ?: "Script $id"
    }

    fun getFile1Name(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FILE1_NAME, "file1.dat") ?: "file1.dat"
    }

    fun getFile2Name(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FILE2_NAME, "file2.dat") ?: "file2.dat"
    }

    fun getStagedTurnOnFile1(context: Context): File {
        val named = File(getTurnOnDir(context), getFile1Name(context))
        if (named.exists() && named.length() > 0) return named
        val fallback = File(getTurnOnDir(context), "file1")
        return if (fallback.exists() && fallback.length() > 0) fallback else named
    }

    fun getStagedTurnOnFile2(context: Context): File {
        val named = File(getTurnOnDir(context), getFile2Name(context))
        if (named.exists() && named.length() > 0) return named
        val fallback = File(getTurnOnDir(context), "file2")
        return if (fallback.exists() && fallback.length() > 0) fallback else named
    }

    fun getStagedTurnOffFile1(context: Context): File {
        val named = File(getTurnOffDir(context), getFile1Name(context))
        if (named.exists() && named.length() > 0) return named
        val fallback = File(getTurnOffDir(context), "file1")
        return if (fallback.exists() && fallback.length() > 0) fallback else named
    }

    fun getStagedTurnOffFile2(context: Context): File {
        val named = File(getTurnOffDir(context), getFile2Name(context))
        if (named.exists() && named.length() > 0) return named
        val fallback = File(getTurnOffDir(context), "file2")
        return if (fallback.exists() && fallback.length() > 0) fallback else named
    }

    fun areFilesStaged(context: Context): Boolean {
        val f1 = getStagedTurnOnFile1(context)
        val f2 = getStagedTurnOnFile2(context)
        val f3 = getStagedTurnOffFile1(context)
        val f4 = getStagedTurnOffFile2(context)
        val filesExist = f1.exists() && f1.length() > 0 &&
                f2.exists() && f2.length() > 0 &&
                f3.exists() && f3.length() > 0 &&
                f4.exists() && f4.length() > 0
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return filesExist && prefs.getBoolean(KEY_FILES_ACTIVATED, false)
    }

    fun isScriptStaged(context: Context, script: ScriptItem): Boolean {
        val scriptDir = getScriptStagedDir(context, script.id)
        val f1 = File(scriptDir, script.file1Name)
        val f2 = File(scriptDir, script.file2Name)
        if (f1.exists() && f1.length() > 0 && f2.exists() && f2.length() > 0) {
            return true
        }
        // Fallback: if this is current active script, check turn_on dir
        if (script.id == getActiveScriptId(context)) {
            val on1 = getStagedTurnOnFile1(context)
            val on2 = getStagedTurnOnFile2(context)
            return on1.exists() && on1.length() > 0 && on2.exists() && on2.length() > 0
        }
        return false
    }

    /**
     * Activates a pre-downloaded script by copying its files into the active turn_on directory.
     */
    fun activateScriptFromStaged(context: Context, script: ScriptItem): Boolean {
        val scriptDir = getScriptStagedDir(context, script.id)
        val f1 = File(scriptDir, script.file1Name)
        val f2 = File(scriptDir, script.file2Name)

        if (!f1.exists() || !f2.exists()) {
            return false
        }

        val destOn1 = File(getTurnOnDir(context), script.file1Name)
        val destOn2 = File(getTurnOnDir(context), script.file2Name)

        try {
            f1.copyTo(destOn1, overwrite = true)
            f2.copyTo(destOn2, overwrite = true)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_FILE1_NAME, script.file1Name)
                .putString(KEY_FILE2_NAME, script.file2Name)
                .putInt(KEY_ACTIVE_SCRIPT_ID, script.id)
                .putString(KEY_ACTIVE_SCRIPT_NAME, script.name)
                .putBoolean(KEY_FILES_ACTIVATED, true)
                .apply()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error activating script from staged: ${e.message}")
            return false
        }
    }

    fun isModOn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MOD_STATE, "OFF") == "ON"
    }

    fun setModState(context: Context, state: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MOD_STATE, state).apply()
    }

    /**
     * Reads default target path 1 from res/raw/targetpath1.txt (falls back to hardcoded default)
     */
    fun getRawDefaultTargetPath1(context: Context): String {
        return try {
            val resId = context.resources.getIdentifier("targetpath1", "raw", context.packageName)
            if (resId != 0) {
                val text = context.resources.openRawResource(resId).bufferedReader().use { it.readText() }.trim()
                if (text.isNotEmpty()) text else DEFAULT_TARGET_PATH_1
            } else DEFAULT_TARGET_PATH_1
        } catch (e: Exception) {
            DEFAULT_TARGET_PATH_1
        }
    }

    /**
     * Reads default target path 2 from res/raw/targetpath2.txt (falls back to hardcoded default)
     */
    fun getRawDefaultTargetPath2(context: Context): String {
        return try {
            val resId = context.resources.getIdentifier("targetpath2", "raw", context.packageName)
            if (resId != 0) {
                val text = context.resources.openRawResource(resId).bufferedReader().use { it.readText() }.trim()
                if (text.isNotEmpty()) text else DEFAULT_TARGET_PATH_2
            } else DEFAULT_TARGET_PATH_2
        } catch (e: Exception) {
            DEFAULT_TARGET_PATH_2
        }
    }

    /**
     * Fetch dynamic target paths from Vercel endpoints (or cached / res/raw fallback).
     */
    fun fetchTargetPath(urlStr: String, fallbackPath: String, context: Context, prefKey: String): String {
        try {
            val request = Request.Builder()
                .url(urlStr)
                .header("Cache-Control", "no-cache")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim()
                    if (!body.isNullOrEmpty() && (body.startsWith("/") || body.startsWith("storage/"))) {
                        val path = if (body.startsWith("/")) body else "/$body"
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putString(prefKey, path).apply()
                        return path
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching target path from $urlStr: ${e.message}")
        }

        // Return cached or fallback from res/raw
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(prefKey, fallbackPath) ?: fallbackPath
    }

    fun getTargetPaths(context: Context): Pair<String, String> {
        val def1 = getRawDefaultTargetPath1(context)
        val def2 = getRawDefaultTargetPath2(context)
        val path1 = fetchTargetPath(VERCEL_TARGET_1_URL, def1, context, KEY_TARGET_PATH_1)
        val path2 = fetchTargetPath(VERCEL_TARGET_2_URL, def2, context, KEY_TARGET_PATH_2)
        return Pair(path1, path2)
    }

    /**
     * Extracts filename from a URL or returns fallback name.
     */
    fun extractFileNameFromUrl(url: String): String {
        return try {
            val clean = url.substringBefore("?").substringBefore("#")
            clean.substringAfterLast("/").trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Parse document to discover all available scripts dynamically.
     */
    fun parseDocumentToConfig(doc: DocumentSnapshot): ScriptsConfig? {
        if (!doc.exists()) return null

        fun getAny(vararg keys: String): String? {
            for (k in keys) {
                val v = doc.getString(k)
                if (!v.isNullOrBlank()) return v.trim()
            }
            return null
        }

        // Also check map structures
        val turnOffMap = doc.get("turn_off") as? Map<*, *>

        val off1 = getAny("turn_off_file1_url", "turn_off_file1", "turn_off_file_1", "turnOff1", "file1_off", "off_file1")
            ?: (turnOffMap?.get("file1") as? String)
            ?: (turnOffMap?.get("file1_url") as? String)

        val off2 = getAny("turn_off_file2_url", "turn_off_file2", "turn_off_file_2", "turnOff2", "file2_off", "off_file2")
            ?: (turnOffMap?.get("file2") as? String)
            ?: (turnOffMap?.get("file2_url") as? String)

        if (off1.isNullOrBlank() || off2.isNullOrBlank()) {
            return null
        }

        val discoveredScripts = mutableListOf<ScriptItem>()
        val foundIds = mutableSetOf<Int>()

        // 1. Check doc data map keys for any patterns like turn_on_file1_url, turn_on_file1_url2, etc.
        val dataMap = doc.data ?: emptyMap<String, Any>()
        for (key in dataMap.keys) {
            val lower = key.lowercase()
            if (lower.startsWith("turn_on_file1_url") || lower.startsWith("turn_on_file1_")) {
                val suffix = lower.removePrefix("turn_on_file1_url").removePrefix("turn_on_file1_").removePrefix("_")
                val id = if (suffix.isEmpty() || suffix == "1") 1 else suffix.toIntOrNull() ?: 1
                foundIds.add(id)
            }
        }

        // 2. Also ensure standard sequential indices 1..20 are checked
        for (i in 1..20) {
            foundIds.add(i)
        }

        val sortedIds = foundIds.sorted()
        for (id in sortedIds) {
            val on1: String?
            val on2: String?
            val name: String?

            if (id == 1) {
                val turnOnMap = doc.get("turn_on") as? Map<*, *>
                on1 = getAny("turn_on_file1_url", "turn_on_file1_url1", "turn_on_file1", "turn_on_file_1", "turnOn1", "file1_on")
                    ?: (turnOnMap?.get("file1") as? String)
                    ?: (turnOnMap?.get("file1_url") as? String)

                on2 = getAny("turn_on_file2_url", "turn_on_file2_url1", "turn_on_file2", "turn_on_file_2", "turnOn2", "file2_on")
                    ?: (turnOnMap?.get("file2") as? String)
                    ?: (turnOnMap?.get("file2_url") as? String)

                name = getAny("script1_name", "script_name1", "script_name_1", "script1_title", "script_title1") ?: "Script 1"
            } else {
                on1 = getAny(
                    "turn_on_file1_url$id",
                    "turn_on_file1_url_$id",
                    "turn_on_file1_$id",
                    "turn_on_file_1_$id",
                    "turnOn1_$id"
                )
                on2 = getAny(
                    "turn_on_file2_url$id",
                    "turn_on_file2_url_$id",
                    "turn_on_file2_$id",
                    "turn_on_file_2_$id",
                    "turnOn2_$id"
                )
                name = getAny(
                    "script${id}_name",
                    "script_name$id",
                    "script_name_$id",
                    "script${id}_title",
                    "script_title$id"
                ) ?: "Script $id"
            }

            if (!on1.isNullOrBlank() && !on2.isNullOrBlank()) {
                val f1Name = extractFileNameFromUrl(on1).ifEmpty {
                    extractFileNameFromUrl(off1).ifEmpty {
                        getAny("file1_name", "target_file1_name") ?: "file1.dat"
                    }
                }
                val f2Name = extractFileNameFromUrl(on2).ifEmpty {
                    extractFileNameFromUrl(off2).ifEmpty {
                        getAny("file2_name", "target_file2_name") ?: "file2.dat"
                    }
                }

                discoveredScripts.add(
                    ScriptItem(
                        id = id,
                        name = name,
                        turnOnFile1Url = on1,
                        turnOnFile2Url = on2,
                        file1Name = f1Name,
                        file2Name = f2Name,
                        rawImageResName = "script$id"
                    )
                )
            }
        }

        if (discoveredScripts.isEmpty()) {
            return null
        }

        val globalF1 = extractFileNameFromUrl(off1).ifEmpty {
            discoveredScripts[0].file1Name
        }
        val globalF2 = extractFileNameFromUrl(off2).ifEmpty {
            discoveredScripts[0].file2Name
        }

        val targetPath1 = getAny("target_path_1", "targetpath1", "target_path1")
        val targetPath2 = getAny("target_path_2", "targetpath2", "target_path2")

        return ScriptsConfig(
            scripts = discoveredScripts,
            turnOffFile1Url = off1,
            turnOffFile2Url = off2,
            file1Name = globalF1,
            file2Name = globalF2,
            targetPath1Override = targetPath1,
            targetPath2Override = targetPath2
        )
    }

    /**
     * Fetch complete multiple-scripts configuration from Firestore.
     */
    fun fetchScriptsConfigFromFirestore(
        context: Context,
        onSuccess: (ScriptsConfig) -> Unit,
        onError: (String) -> Unit
    ) {
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("app_settings").document("global").get()
            .addOnSuccessListener { docGlobal ->
                val cfg1 = parseDocumentToConfig(docGlobal)
                if (cfg1 != null) {
                    saveScriptsConfigToCache(context, cfg1)
                    onSuccess(cfg1)
                    return@addOnSuccessListener
                }

                firestore.collection("app_settings").document("files").get()
                    .addOnSuccessListener { docFiles ->
                        val cfg2 = parseDocumentToConfig(docFiles)
                        if (cfg2 != null) {
                            saveScriptsConfigToCache(context, cfg2)
                            onSuccess(cfg2)
                            return@addOnSuccessListener
                        }

                        firestore.collection("config").document("files").get()
                            .addOnSuccessListener { docConfig ->
                                val cfg3 = parseDocumentToConfig(docConfig)
                                if (cfg3 != null) {
                                    saveScriptsConfigToCache(context, cfg3)
                                    onSuccess(cfg3)
                                } else {
                                    // Try loading cached config if Firestore doc had missing fields
                                    val cached = loadScriptsConfigFromCache(context)
                                    if (cached != null) {
                                        onSuccess(cached)
                                    } else {
                                        onError(
                                            "Could not find scripts config in Firestore.\n" +
                                            "Please ensure 'turn_on_file1_url' and 'turn_on_file2_url' (or multiple scripts) " +
                                            "and 'turn_off_file1_url' & 'turn_off_file2_url' exist in app_settings/global."
                                        )
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                val cached = loadScriptsConfigFromCache(context)
                                if (cached != null) onSuccess(cached)
                                else onError("Failed to read Firestore config: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        val cached = loadScriptsConfigFromCache(context)
                        if (cached != null) onSuccess(cached)
                        else onError("Failed to read Firestore app_settings/files: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                val cached = loadScriptsConfigFromCache(context)
                if (cached != null) onSuccess(cached)
                else onError("Failed to read Firestore app_settings/global: ${e.message}")
            }
    }

    private fun saveScriptsConfigToCache(context: Context, config: ScriptsConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt(KEY_CACHED_SCRIPTS_COUNT, config.scripts.size)
        editor.putString("cache_turn_off_1", config.turnOffFile1Url)
        editor.putString("cache_turn_off_2", config.turnOffFile2Url)
        for ((idx, script) in config.scripts.withIndex()) {
            editor.putInt("cache_s_${idx}_id", script.id)
            editor.putString("cache_s_${idx}_name", script.name)
            editor.putString("cache_s_${idx}_url1", script.turnOnFile1Url)
            editor.putString("cache_s_${idx}_url2", script.turnOnFile2Url)
            editor.putString("cache_s_${idx}_f1", script.file1Name)
            editor.putString("cache_s_${idx}_f2", script.file2Name)
        }
        editor.apply()
    }

    private fun loadScriptsConfigFromCache(context: Context): ScriptsConfig? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_CACHED_SCRIPTS_COUNT, 0)
        val off1 = prefs.getString("cache_turn_off_1", null)
        val off2 = prefs.getString("cache_turn_off_2", null)
        if (count == 0 || off1 == null || off2 == null) return null

        val list = mutableListOf<ScriptItem>()
        for (i in 0 until count) {
            val id = prefs.getInt("cache_s_${i}_id", i + 1)
            val name = prefs.getString("cache_s_${i}_name", "Script $id") ?: "Script $id"
            val url1 = prefs.getString("cache_s_${i}_url1", "") ?: ""
            val url2 = prefs.getString("cache_s_${i}_url2", "") ?: ""
            val f1 = prefs.getString("cache_s_${i}_f1", "file1.dat") ?: "file1.dat"
            val f2 = prefs.getString("cache_s_${i}_f2", "file2.dat") ?: "file2.dat"
            if (url1.isNotEmpty() && url2.isNotEmpty()) {
                list.add(ScriptItem(id, name, url1, url2, f1, f2))
            }
        }
        if (list.isEmpty()) return null
        return ScriptsConfig(
            scripts = list,
            turnOffFile1Url = off1,
            turnOffFile2Url = off2,
            file1Name = list[0].file1Name,
            file2Name = list[0].file2Name
        )
    }

    /**
     * Backwards compatible single manifest fetcher.
     */
    fun fetchManifestFromFirestore(
        context: Context,
        onSuccess: (FileManifest) -> Unit,
        onError: (String) -> Unit
    ) {
        fetchScriptsConfigFromFirestore(
            context = context,
            onSuccess = { cfg ->
                val activeId = getActiveScriptId(context)
                val targetScript = cfg.scripts.find { it.id == activeId } ?: cfg.scripts.first()
                onSuccess(
                    FileManifest(
                        turnOnFile1Url = targetScript.turnOnFile1Url,
                        turnOnFile2Url = targetScript.turnOnFile2Url,
                        turnOffFile1Url = cfg.turnOffFile1Url,
                        turnOffFile2Url = cfg.turnOffFile2Url,
                        file1Name = targetScript.file1Name,
                        file2Name = targetScript.file2Name,
                        targetPath1Override = cfg.targetPath1Override,
                        targetPath2Override = cfg.targetPath2Override
                    )
                )
            },
            onError = onError
        )
    }

    /**
     * Downloads a single file via OkHttp with byte progress.
     */
    private fun downloadFile(url: String, destFile: File, onProgress: (Long, Long) -> Unit): Boolean {
        val request = Request.Builder().url(url).build()
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    lastLog = "Download failed ($url): HTTP ${response.code}"
                    return false
                }
                val body = response.body ?: run {
                    lastLog = "Response body is empty ($url)"
                    return false
                }

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            onProgress(downloadedBytes, totalBytes)
                        }
                        output.flush()
                    }
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (destFile.exists()) destFile.delete()
                tempFile.renameTo(destFile)
                destFile.setReadable(true, false)
                return true
            }
        } catch (e: Exception) {
            lastLog = "Download error ($url): ${e.message}"
            return false
        }

        lastLog = "Downloaded file is empty or missing ($url)"
        return false
    }

    /**
     * Downloads and stages a specific script and the turn off files.
     */
    fun downloadAndStageSpecificScript(
        context: Context,
        script: ScriptItem,
        turnOffFile1Url: String,
        turnOffFile2Url: String,
        onProgress: (status: String, percent: Int) -> Unit,
        onComplete: (success: Boolean, errorMsg: String?) -> Unit
    ) {
        Thread {
            try {
                val scriptDir = getScriptStagedDir(context, script.id)
                val f1Script = File(scriptDir, script.file1Name)
                val f2Script = File(scriptDir, script.file2Name)

                val turnOnDir = getTurnOnDir(context)
                val f1On = File(turnOnDir, script.file1Name)
                val f2On = File(turnOnDir, script.file2Name)

                val turnOffDir = getTurnOffDir(context)
                val f1Off = File(turnOffDir, script.file1Name)
                val f2Off = File(turnOffDir, script.file2Name)

                // 1. Download Turn ON File 1 for this script
                onProgress("Downloading ${script.name} (File 1/2)...", 10)
                if (!downloadFile(script.turnOnFile1Url, f1Script) { cur, total ->
                        val p = if (total > 0) 10 + ((cur * 20) / total).toInt() else 20
                        onProgress("Downloading ${script.name} (File 1/2)...", p)
                    }) {
                    onComplete(false, "Failed to download ${script.name} File 1: $lastLog")
                    return@Thread
                }
                // Also mirror to active turn_on directory
                f1Script.copyTo(f1On, overwrite = true)

                // 2. Download Turn ON File 2 for this script
                onProgress("Downloading ${script.name} (File 2/2)...", 35)
                if (!downloadFile(script.turnOnFile2Url, f2Script) { cur, total ->
                        val p = if (total > 0) 35 + ((cur * 20) / total).toInt() else 45
                        onProgress("Downloading ${script.name} (File 2/2)...", p)
                    }) {
                    onComplete(false, "Failed to download ${script.name} File 2: $lastLog")
                    return@Thread
                }
                // Also mirror to active turn_on directory
                f2Script.copyTo(f2On, overwrite = true)

                // 3. Download Turn OFF File 1 (if needed or update)
                onProgress("Downloading Turn OFF (File 1/2)...", 60)
                if (!downloadFile(turnOffFile1Url, f1Off) { cur, total ->
                        val p = if (total > 0) 60 + ((cur * 20) / total).toInt() else 70
                        onProgress("Downloading Turn OFF (File 1/2)...", p)
                    }) {
                    onComplete(false, "Failed to download Turn OFF File 1: $lastLog")
                    return@Thread
                }

                // 4. Download Turn OFF File 2
                onProgress("Downloading Turn OFF (File 2/2)...", 85)
                if (!downloadFile(turnOffFile2Url, f2Off) { cur, total ->
                        val p = if (total > 0) 85 + ((cur * 15) / total).toInt() else 95
                        onProgress("Downloading Turn OFF (File 2/2)...", p)
                    }) {
                    onComplete(false, "Failed to download Turn OFF File 2: $lastLog")
                    return@Thread
                }

                // Update preferences
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_FILE1_NAME, script.file1Name)
                    .putString(KEY_FILE2_NAME, script.file2Name)
                    .putInt(KEY_ACTIVE_SCRIPT_ID, script.id)
                    .putString(KEY_ACTIVE_SCRIPT_NAME, script.name)
                    .putBoolean(KEY_FILES_ACTIVATED, true)
                    .putString(KEY_MOD_STATE, "OFF")
                    .apply()

                onProgress("Staging complete! ${script.name} ready.", 100)
                onComplete(true, null)
            } catch (e: Exception) {
                e.printStackTrace()
                lastLog = e.message ?: "Unknown download error"
                onComplete(false, "Download error: ${e.message}")
            }
        }.start()
    }

    /**
     * Downloads files for a single FileManifest (backwards compatibility).
     */
    fun downloadAndStageAllFiles(
        context: Context,
        manifest: FileManifest,
        onProgress: (status: String, percent: Int) -> Unit,
        onComplete: (success: Boolean, errorMsg: String?) -> Unit
    ) {
        val scriptItem = ScriptItem(
            id = getActiveScriptId(context),
            name = getActiveScriptName(context),
            turnOnFile1Url = manifest.turnOnFile1Url,
            turnOnFile2Url = manifest.turnOnFile2Url,
            file1Name = manifest.file1Name,
            file2Name = manifest.file2Name
        )
        downloadAndStageSpecificScript(
            context = context,
            script = scriptItem,
            turnOffFile1Url = manifest.turnOffFile1Url,
            turnOffFile2Url = manifest.turnOffFile2Url,
            onProgress = onProgress,
            onComplete = onComplete
        )
    }

    /**
     * Normalizes a target path and splits into (Directory, optional SpecificFileName).
     */
    private fun resolveTargetLocation(targetPath: String, defaultFileName: String): Pair<String, String> {
        val trimmed = targetPath.trim()
        val file = File(trimmed)

        return if (trimmed.endsWith("/") || file.isDirectory || !trimmed.substringAfterLast("/").contains(".")) {
            val dirPath = if (trimmed.endsWith("/")) trimmed.dropLast(1) else trimmed
            Pair(dirPath, defaultFileName)
        } else {
            val parent = file.parent ?: "/storage/emulated/0"
            val name = file.name
            Pair(parent, name)
        }
    }

    /**
     * Checks if directory exists WITHOUT creating it.
     */
    fun checkDirectoryExists(dirPath: String): Boolean {
        return RenameUtil.checkDirExists(dirPath)
    }

    /**
     * Copies a file to the destination.
     * If an existing file with the same name exists, it deletes it first.
     */
    private fun copyFileToDestination(srcFile: File, destFile: File): Boolean {
        val destPath = destFile.absolutePath
        val srcPath = srcFile.absolutePath

        // 1. Delete old file if present
        try {
            if (destFile.exists()) {
                destFile.delete()
            }
            if (RenameUtil.useShizukuOps && RenameUtil.shizukuAvailable()) {
                RenameUtil.executeShizukuCommand("rm -f \"$destPath\" 2>/dev/null")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing old file: ${e.message}")
        }

        // 2. Try standard Java stream copy
        var copySuccess = false
        try {
            srcFile.inputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            copySuccess = destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            Log.w(TAG, "Standard copy failed, trying Shizuku: ${e.message}")
            copySuccess = false
        }

        if (copySuccess) return true

        // 3. Fallback to Shizuku if standard copy failed
        if (RenameUtil.shizukuAvailable()) {
            srcFile.setReadable(true, false)
            val cmd = "cp -f \"$srcPath\" \"$destPath\""
            val ok = RenameUtil.executeShizukuCommand(cmd)
            if (ok && RenameUtil.checkFileExists(destPath)) {
                return true
            }
        }

        lastLog = "Failed to copy $srcPath to $destPath"
        return false
    }

    /**
     * Apply TURN ON:
     * Copies Turn ON File 1 to Target Path 1 (deleting old).
     * Copies Turn ON File 2 to Target Path 2 (deleting old).
     */
    fun applyTurnOn(context: Context): Pair<Boolean, String> {
        if (!areFilesStaged(context)) {
            val err = "App is not activated! Please click 'ACTIVATE FIRST TIME' to download files."
            lastLog = err
            return Pair(false, err)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultFile1Name = prefs.getString(KEY_FILE1_NAME, "file1.dat") ?: "file1.dat"
        val defaultFile2Name = prefs.getString(KEY_FILE2_NAME, "file2.dat") ?: "file2.dat"

        val (rawPath1, rawPath2) = getTargetPaths(context)
        val (dir1, targetFileName1) = resolveTargetLocation(rawPath1, defaultFile1Name)
        val (dir2, targetFileName2) = resolveTargetLocation(rawPath2, defaultFile2Name)

        if (!checkDirectoryExists(dir1)) {
            val err = "Destination path 1 does NOT exist:\n'$dir1'\nDirectory creation is disabled as per policy. Please ensure the target folder exists."
            lastLog = err
            Log.e(TAG, err)
            return Pair(false, err)
        }

        if (!checkDirectoryExists(dir2)) {
            val err = "Destination path 2 does NOT exist:\n'$dir2'\nDirectory creation is disabled as per policy. Please ensure the target folder exists."
            lastLog = err
            Log.e(TAG, err)
            return Pair(false, err)
        }

        val destFile1 = File(dir1, targetFileName1)
        val destFile2 = File(dir2, targetFileName2)

        val srcFile1 = getStagedTurnOnFile1(context)
        val srcFile2 = getStagedTurnOnFile2(context)

        // Copy File 1
        if (!copyFileToDestination(srcFile1, destFile1)) {
            val err = "Failed to place Turn ON File 1 into $destFile1: $lastLog"
            lastLog = err
            return Pair(false, err)
        }

        // Copy File 2
        if (!copyFileToDestination(srcFile2, destFile2)) {
            val err = "Failed to place Turn ON File 2 into $destFile2: $lastLog"
            lastLog = err
            return Pair(false, err)
        }

        setModState(context, "ON")
        val activeName = getActiveScriptName(context)
        val msg = "$activeName turned ON successfully!\nPlaced:\n• ${destFile1.absolutePath}\n• ${destFile2.absolutePath}"
        lastLog = msg
        Log.i(TAG, msg)
        return Pair(true, msg)
    }

    /**
     * Apply TURN OFF:
     * Copies Turn OFF File 1 to Target Path 1 (deleting old).
     * Copies Turn OFF File 2 to Target Path 2 (deleting old).
     */
    fun applyTurnOff(context: Context): Pair<Boolean, String> {
        if (!areFilesStaged(context)) {
            val err = "App is not activated! Please click 'ACTIVATE FIRST TIME' to download files."
            lastLog = err
            return Pair(false, err)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultFile1Name = prefs.getString(KEY_FILE1_NAME, "file1.dat") ?: "file1.dat"
        val defaultFile2Name = prefs.getString(KEY_FILE2_NAME, "file2.dat") ?: "file2.dat"

        val (rawPath1, rawPath2) = getTargetPaths(context)
        val (dir1, targetFileName1) = resolveTargetLocation(rawPath1, defaultFile1Name)
        val (dir2, targetFileName2) = resolveTargetLocation(rawPath2, defaultFile2Name)

        if (!checkDirectoryExists(dir1)) {
            val err = "Destination path 1 does NOT exist:\n'$dir1'\nDirectory creation is disabled as per policy. Please ensure the target folder exists."
            lastLog = err
            Log.e(TAG, err)
            return Pair(false, err)
        }

        if (!checkDirectoryExists(dir2)) {
            val err = "Destination path 2 does NOT exist:\n'$dir2'\nDirectory creation is disabled as per policy. Please ensure the target folder exists."
            lastLog = err
            Log.e(TAG, err)
            return Pair(false, err)
        }

        val destFile1 = File(dir1, targetFileName1)
        val destFile2 = File(dir2, targetFileName2)

        val srcFile1 = getStagedTurnOffFile1(context)
        val srcFile2 = getStagedTurnOffFile2(context)

        // Copy File 1
        if (!copyFileToDestination(srcFile1, destFile1)) {
            val err = "Failed to place Turn OFF File 1 into $destFile1: $lastLog"
            lastLog = err
            return Pair(false, err)
        }

        // Copy File 2
        if (!copyFileToDestination(srcFile2, destFile2)) {
            val err = "Failed to place Turn OFF File 2 into $destFile2: $lastLog"
            lastLog = err
            return Pair(false, err)
        }

        setModState(context, "OFF")
        val msg = "Turn OFF successful!\nPlaced:\n• ${destFile1.absolutePath}\n• ${destFile2.absolutePath}"
        lastLog = msg
        Log.i(TAG, msg)
        return Pair(true, msg)
    }
}
