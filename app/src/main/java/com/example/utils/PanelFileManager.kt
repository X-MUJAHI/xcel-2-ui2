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
import java.io.InputStream
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
     * Fetch file configuration from Firestore.
     * Searches app_settings/global, app_settings/files, and config/files.
     */
    fun fetchManifestFromFirestore(
        context: Context,
        onSuccess: (FileManifest) -> Unit,
        onError: (String) -> Unit
    ) {
        val firestore = FirebaseFirestore.getInstance()

        fun parseDocument(doc: DocumentSnapshot): FileManifest? {
            if (!doc.exists()) return null

            // Helper to get string from various naming conventions
            fun getAny(vararg keys: String): String? {
                for (k in keys) {
                    val v = doc.getString(k)
                    if (!v.isNullOrBlank()) return v.trim()
                }
                return null
            }

            // Also check map structures (e.g. turn_on.file1)
            val turnOnMap = doc.get("turn_on") as? Map<*, *>
            val turnOffMap = doc.get("turn_off") as? Map<*, *>

            val on1 = getAny("turn_on_file1_url", "turn_on_file1", "turn_on_file_1", "turnOn1", "file1_on", "on_file1")
                ?: (turnOnMap?.get("file1") as? String)
                ?: (turnOnMap?.get("file1_url") as? String)

            val on2 = getAny("turn_on_file2_url", "turn_on_file2", "turn_on_file_2", "turnOn2", "file2_on", "on_file2")
                ?: (turnOnMap?.get("file2") as? String)
                ?: (turnOnMap?.get("file2_url") as? String)

            val off1 = getAny("turn_off_file1_url", "turn_off_file1", "turn_off_file_1", "turnOff1", "file1_off", "off_file1")
                ?: (turnOffMap?.get("file1") as? String)
                ?: (turnOffMap?.get("file1_url") as? String)

            val off2 = getAny("turn_off_file2_url", "turn_off_file2", "turn_off_file_2", "turnOff2", "file2_off", "off_file2")
                ?: (turnOffMap?.get("file2") as? String)
                ?: (turnOffMap?.get("file2_url") as? String)

            if (on1.isNullOrBlank() || on2.isNullOrBlank() || off1.isNullOrBlank() || off2.isNullOrBlank()) {
                return null
            }

            // Master file names are strictly preserved from the actual files (extracted from URLs)
            val f1Name = extractFileNameFromUrl(on1).ifEmpty {
                extractFileNameFromUrl(off1).ifEmpty {
                    getAny("file1_name", "target_file1_name", "turn_on_file1_name", "file1Name") ?: "file1"
                }
            }

            val f2Name = extractFileNameFromUrl(on2).ifEmpty {
                extractFileNameFromUrl(off2).ifEmpty {
                    getAny("file2_name", "target_file2_name", "turn_on_file2_name", "file2Name") ?: "file2"
                }
            }

            val targetPath1 = getAny("target_path_1", "targetpath1", "target_path1")
            val targetPath2 = getAny("target_path_2", "targetpath2", "target_path2")

            return FileManifest(
                turnOnFile1Url = on1,
                turnOnFile2Url = on2,
                turnOffFile1Url = off1,
                turnOffFile2Url = off2,
                file1Name = f1Name,
                file2Name = f2Name,
                targetPath1Override = targetPath1,
                targetPath2Override = targetPath2
            )
        }

        // 1. Try app_settings/global
        firestore.collection("app_settings").document("global").get()
            .addOnSuccessListener { docGlobal ->
                val manifest1 = parseDocument(docGlobal)
                if (manifest1 != null) {
                    onSuccess(manifest1)
                    return@addOnSuccessListener
                }

                // 2. Try app_settings/files
                firestore.collection("app_settings").document("files").get()
                    .addOnSuccessListener { docFiles ->
                        val manifest2 = parseDocument(docFiles)
                        if (manifest2 != null) {
                            onSuccess(manifest2)
                            return@addOnSuccessListener
                        }

                        // 3. Try config/files
                        firestore.collection("config").document("files").get()
                            .addOnSuccessListener { docConfig ->
                                val manifest3 = parseDocument(docConfig)
                                if (manifest3 != null) {
                                    onSuccess(manifest3)
                                } else {
                                    onError(
                                        "Could not find 4 file links in Firestore.\n" +
                                        "Please configure 'app_settings/global' or 'app_settings/files' with:\n" +
                                        "• turn_on_file1_url\n" +
                                        "• turn_on_file2_url\n" +
                                        "• turn_off_file1_url\n" +
                                        "• turn_off_file2_url"
                                    )
                                }
                            }
                            .addOnFailureListener { e ->
                                onError("Failed to read Firestore config: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        onError("Failed to read Firestore app_settings/files: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                onError("Failed to read Firestore app_settings/global: ${e.message}")
            }
    }

    private fun extractFileNameFromUrl(url: String): String {
        return try {
            val clean = url.substringBefore("?").substringBefore("#")
            clean.substringAfterLast("/").trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Downloads a single file via OkHttp with byte progress.
     */
    private fun downloadFile(url: String, destFile: File, onProgress: (Long, Long) -> Unit): Boolean {
        val request = Request.Builder().url(url).build()
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()

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
        lastLog = "Downloaded file is empty or missing ($url)"
        return false
    }

    /**
     * Downloads all 4 files and stages them permanently.
     */
    fun downloadAndStageAllFiles(
        context: Context,
        manifest: FileManifest,
        onProgress: (status: String, percent: Int) -> Unit,
        onComplete: (success: Boolean, errorMsg: String?) -> Unit
    ) {
        Thread {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_FILE1_NAME, manifest.file1Name)
                    .putString(KEY_FILE2_NAME, manifest.file2Name)
                    .apply()

                val f1On = File(getTurnOnDir(context), manifest.file1Name)
                val f2On = File(getTurnOnDir(context), manifest.file2Name)
                val f1Off = File(getTurnOffDir(context), manifest.file1Name)
                val f2Off = File(getTurnOffDir(context), manifest.file2Name)

                // 1. Download Turn ON File 1
                onProgress("Downloading Turn ON (File 1/2)...", 10)
                if (!downloadFile(manifest.turnOnFile1Url, f1On) { cur, total ->
                        val p = if (total > 0) 10 + ((cur * 20) / total).toInt() else 20
                        onProgress("Downloading Turn ON (File 1/2)...", p)
                    }) {
                    onComplete(false, "Failed to download Turn ON File 1: $lastLog")
                    return@Thread
                }

                // 2. Download Turn ON File 2
                onProgress("Downloading Turn ON (File 2/2)...", 35)
                if (!downloadFile(manifest.turnOnFile2Url, f2On) { cur, total ->
                        val p = if (total > 0) 35 + ((cur * 20) / total).toInt() else 45
                        onProgress("Downloading Turn ON (File 2/2)...", p)
                    }) {
                    onComplete(false, "Failed to download Turn ON File 2: $lastLog")
                    return@Thread
                }

                // 3. Download Turn OFF File 1
                onProgress("Downloading Turn OFF (File 1/2)...", 60)
                if (!downloadFile(manifest.turnOffFile1Url, f1Off) { cur, total ->
                        val p = if (total > 0) 60 + ((cur * 20) / total).toInt() else 70
                        onProgress("Downloading Turn OFF (File 1/2)...", p)
                    }) {
                    onComplete(false, "Failed to download Turn OFF File 1: $lastLog")
                    return@Thread
                }

                // 4. Download Turn OFF File 2
                onProgress("Downloading Turn OFF (File 2/2)...", 85)
                if (!downloadFile(manifest.turnOffFile2Url, f2Off) { cur, total ->
                        val p = if (total > 0) 85 + ((cur * 15) / total).toInt() else 95
                        onProgress("Downloading Turn OFF (File 2/2)...", p)
                    }) {
                    onComplete(false, "Failed to download Turn OFF File 2: $lastLog")
                    return@Thread
                }

                // Save filenames and mark activated
                prefs.edit()
                    .putString(KEY_FILE1_NAME, manifest.file1Name)
                    .putString(KEY_FILE2_NAME, manifest.file2Name)
                    .putBoolean(KEY_FILES_ACTIVATED, true)
                    .putString(KEY_MOD_STATE, "OFF")
                    .apply()

                onProgress("Activation complete! All 4 files ready.", 100)
                onComplete(true, null)
            } catch (e: Exception) {
                e.printStackTrace()
                lastLog = e.message ?: "Unknown download error"
                onComplete(false, "Download error: ${e.message}")
            }
        }.start()
    }

    /**
     * Normalizes a target path and splits into (Directory, optional SpecificFileName).
     */
    private fun resolveTargetLocation(targetPath: String, defaultFileName: String): Pair<String, String> {
        val trimmed = targetPath.trim()
        val file = File(trimmed)

        // If targetPath looks like a directory path (ends with / or doesn't have an extension)
        return if (trimmed.endsWith("/") || file.isDirectory || !trimmed.substringAfterLast("/").contains(".")) {
            val dirPath = if (trimmed.endsWith("/")) trimmed.dropLast(1) else trimmed
            Pair(dirPath, defaultFileName)
        } else {
            // It specifies a direct file path
            val parent = file.parent ?: "/storage/emulated/0"
            val name = file.name
            Pair(parent, name)
        }
    }

    /**
     * Checks if directory exists or creates it if missing.
     * Uses Shizuku if available, otherwise File.mkdirs().
     */
    fun ensureDirectoryExists(dirPath: String): Boolean {
        if (dirPath.isBlank()) return false
        val dir = File(dirPath)
        if (dir.exists() && dir.isDirectory) return true

        // Try standard Java mkdirs
        try {
            if (dir.mkdirs()) return true
        } catch (e: Exception) {
            Log.w(TAG, "Standard mkdirs failed for $dirPath: ${e.message}")
        }

        // Try Shizuku mkdir -p
        if (RenameUtil.useShizukuOps && RenameUtil.shizukuAvailable()) {
            val ok = RenameUtil.executeShizukuCommand("mkdir -p \"$dirPath\"")
            if (ok && RenameUtil.checkDirExists(dirPath)) {
                return true
            }
        }

        return RenameUtil.checkDirExists(dirPath) || dir.exists()
    }

    /**
     * Checks if directory exists.
     */
    fun checkDirectoryExists(dirPath: String): Boolean {
        return RenameUtil.checkDirExists(dirPath) || File(dirPath).exists()
    }

    /**
     * Copies a file to the destination.
     * If destination directory does not exist, creates it.
     * If an existing file with the same name exists, it deletes it first.
     */
    private fun copyFileToDestination(srcFile: File, destFile: File): Boolean {
        val destPath = destFile.absolutePath
        val srcPath = srcFile.absolutePath

        if (!srcFile.exists() || srcFile.length() == 0L) {
            lastLog = "Source file missing or empty: $srcPath"
            return false
        }

        // Ensure target directory exists (create if empty or new)
        val parentDir = destFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            ensureDirectoryExists(parentDir.absolutePath)
        }

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
            val parentPath = destFile.parent ?: ""
            if (parentPath.isNotEmpty()) {
                RenameUtil.executeShizukuCommand("mkdir -p \"$parentPath\"")
            }
            val cmd = "cp -f \"$srcPath\" \"$destPath\""
            val ok = RenameUtil.executeShizukuCommand(cmd)
            if (ok && RenameUtil.checkFileExists(destPath)) {
                return true
            }
        }

        lastLog = "Failed to copy $srcPath to $destPath (Shizuku: ${RenameUtil.shizukuAvailable()}, error: ${RenameUtil.lastError})"
        return false
    }

    /**
     * Apply TURN ON:
     * Copies Turn ON File 1 to Target Path 1 (deleting old).
     * Copies Turn ON File 2 to Target Path 2 (deleting old).
     * If destination directory is empty or missing, creates it and proceeds.
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

        // Ensure destination directories exist (create if missing or empty)
        if (!ensureDirectoryExists(dir1)) {
            Log.w(TAG, "Could not verify dir1 existence ($dir1), will attempt direct file creation")
        }

        if (!ensureDirectoryExists(dir2)) {
            Log.w(TAG, "Could not verify dir2 existence ($dir2), will attempt direct file creation")
        }

        val destFile1 = File(dir1, targetFileName1)
        val destFile2 = File(dir2, targetFileName2)

        val srcFile1 = getStagedTurnOnFile1(context)
        val srcFile2 = getStagedTurnOnFile2(context)

        // Copy File 1
        if (!copyFileToDestination(srcFile1, destFile1)) {
            val err = "Failed to copy Turn ON File 1 to ${destFile1.absolutePath}\nDetails: $lastLog"
            lastLog = err
            return Pair(false, err)
        }

        // Copy File 2
        if (!copyFileToDestination(srcFile2, destFile2)) {
            val err = "Failed to copy Turn ON File 2 to ${destFile2.absolutePath}\nDetails: $lastLog"
            lastLog = err
            return Pair(false, err)
        }

        setModState(context, "ON")
        val msg = "Turn ON successful!\nPlaced:\n• ${destFile1.absolutePath}\n• ${destFile2.absolutePath}"
        lastLog = msg
        Log.i(TAG, msg)
        return Pair(true, msg)
    }

    /**
     * Apply TURN OFF:
     * Copies Turn OFF File 1 to Target Path 1 (deleting old).
     * Copies Turn OFF File 2 to Target Path 2 (deleting old).
     * If destination directory is empty or missing, creates it and proceeds.
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

        // Ensure destination directories exist (create if missing or empty)
        if (!ensureDirectoryExists(dir1)) {
            Log.w(TAG, "Could not verify dir1 existence ($dir1), will attempt direct file creation")
        }

        if (!ensureDirectoryExists(dir2)) {
            Log.w(TAG, "Could not verify dir2 existence ($dir2), will attempt direct file creation")
        }

        val destFile1 = File(dir1, targetFileName1)
        val destFile2 = File(dir2, targetFileName2)

        val srcFile1 = getStagedTurnOffFile1(context)
        val srcFile2 = getStagedTurnOffFile2(context)

        // Copy File 1
        if (!copyFileToDestination(srcFile1, destFile1)) {
            val err = "Failed to copy Turn OFF File 1 to ${destFile1.absolutePath}\nDetails: $lastLog"
            lastLog = err
            return Pair(false, err)
        }

        // Copy File 2
        if (!copyFileToDestination(srcFile2, destFile2)) {
            val err = "Failed to copy Turn OFF File 2 to ${destFile2.absolutePath}\nDetails: $lastLog"
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
