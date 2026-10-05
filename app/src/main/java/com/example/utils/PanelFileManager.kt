package com.example.utils

import android.content.Context
import android.os.Environment
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
    private const val PREF_MOD_STATE = "mod_state"
    private const val PREF_IS_STAGED = "files_staged"

    var lastLog: String = ""

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    data class FileManifest(
        val turnOnFile1Url: String = "",
        val turnOnFile2Url: String = "",
        val turnOffFile1Url: String = "",
        val turnOffFile2Url: String = "",
        val file1Name: String = "",
        val file2Name: String = "",
        val targetPath1Override: String = "",
        val targetPath2Override: String = ""
    )

    fun isModOn(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_MOD_STATE, "OFF") == "ON"
    }

    fun setModState(context: Context, state: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_MOD_STATE, state)
            .apply()
    }

    fun areFilesStaged(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stagedPref = prefs.getBoolean(PREF_IS_STAGED, false)
        val file1Name = getFile1Name(context).ifEmpty { "xcel_config.bin" }

        val onFile1 = File(getTurnOnDir(context), file1Name)
        val offFile1 = File(getTurnOffDir(context), file1Name)
        val hasSpecificFiles = onFile1.exists() && onFile1.length() > 0 && offFile1.exists() && offFile1.length() > 0
        val hasAnyStaged = (getTurnOnDir(context).listFiles()?.any { it.length() > 0 } == true) &&
                (getTurnOffDir(context).listFiles()?.any { it.length() > 0 } == true)

        return (stagedPref && (hasSpecificFiles || hasAnyStaged)) || hasSpecificFiles
    }

    fun getStagedBaseDir(context: Context): File {
        val downloadFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "XcelPanel/staged_mods")
        try {
            if (downloadFolder.exists() || downloadFolder.mkdirs()) {
                return downloadFolder
            }
        } catch (_: Exception) {}

        val ext = context.getExternalFilesDir(null)
        if (ext != null) {
            val f = File(ext, "staged_mods")
            try {
                if (f.exists() || f.mkdirs()) return f
            } catch (_: Exception) {}
        }
        return File(context.filesDir, "staged_mods").apply { mkdirs() }
    }

    fun getTurnOnDir(context: Context): File {
        return File(getStagedBaseDir(context), "turn_on").apply { mkdirs() }
    }

    fun getTurnOffDir(context: Context): File {
        return File(getStagedBaseDir(context), "turn_off").apply { mkdirs() }
    }

    fun getFile1Name(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("file1_name", "") ?: ""
    }

    fun getEffectiveFile1Name(context: Context): String {
        val pref = getFile1Name(context).trim()
        if (pref.isNotEmpty()) return pref
        val firstTurnOnFile = getTurnOnDir(context).listFiles()?.firstOrNull { it.isFile && it.length() > 0 }
        if (firstTurnOnFile != null) return firstTurnOnFile.name
        val firstTurnOffFile = getTurnOffDir(context).listFiles()?.firstOrNull { it.isFile && it.length() > 0 }
        if (firstTurnOffFile != null) return firstTurnOffFile.name
        return "xcel_config.bin"
    }

    fun getFile2Name(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("file2_name", "") ?: ""
    }

    fun fetchTargetPath(key: String, rawDefault: String, context: Context, fileName: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val override = prefs.getString(key, null)
        if (!override.isNullOrBlank()) {
            return override.trim()
        }
        val raw = rawDefault.trim()
        return if (raw.endsWith("/")) raw + fileName else "$raw/$fileName"
    }

    fun getRawDefaultTargetPath1(context: Context): String {
        return "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android"
    }

    fun getRawDefaultTargetPath2(context: Context): String {
        return "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional/android"
    }

    fun getTargetPaths(context: Context): Pair<String, String> {
        val f1Name = getFile1Name(context)
        val f2Name = getFile2Name(context)
        val p1 = fetchTargetPath("target_path1_override", getRawDefaultTargetPath1(context), context, f1Name)
        val p2 = if (f2Name.isNotEmpty()) {
            fetchTargetPath("target_path2_override", getRawDefaultTargetPath2(context), context, f2Name)
        } else ""
        return Pair(p1, p2)
    }

    fun fetchManifestFromFirestore(
        context: Context,
        onSuccess: (FileManifest) -> Unit,
        onError: (String) -> Unit
    ) {
        FirebaseFirestore.getInstance()
            .collection("app_settings")
            .document("global")
            .get()
            .addOnSuccessListener { snapshot: DocumentSnapshot? ->
                if (snapshot != null && snapshot.exists()) {
                    val manifest = FileManifest(
                        turnOnFile1Url = snapshot.getString("turnOnFile1Url") ?: "",
                        turnOnFile2Url = snapshot.getString("turnOnFile2Url") ?: "",
                        turnOffFile1Url = snapshot.getString("turnOffFile1Url") ?: "",
                        turnOffFile2Url = snapshot.getString("turnOffFile2Url") ?: "",
                        file1Name = snapshot.getString("file1Name") ?: "",
                        file2Name = snapshot.getString("file2Name") ?: "",
                        targetPath1Override = snapshot.getString("targetPath1Override") ?: "",
                        targetPath2Override = snapshot.getString("targetPath2Override") ?: ""
                    )
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("file1_name", manifest.file1Name)
                        .putString("file2_name", manifest.file2Name)
                        .putString("target_path1_override", manifest.targetPath1Override)
                        .putString("target_path2_override", manifest.targetPath2Override)
                        .apply()
                    onSuccess(manifest)
                } else {
                    onError("Configuration document does not exist.")
                }
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Firestore error")
            }
    }

    fun downloadAndStageAllFiles(
        context: Context,
        manifest: FileManifest,
        onProgress: (String, Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        Thread {
            try {
                val onDir = getTurnOnDir(context)
                val offDir = getTurnOffDir(context)

                // Clean staging directories to ensure old scripts are completely replaced
                try {
                    onDir.listFiles()?.forEach { it.delete() }
                    offDir.listFiles()?.forEach { it.delete() }
                } catch (_: Exception) {}

                val file1Name = if (manifest.file1Name.isNotBlank()) manifest.file1Name.trim() else "xcel_config.bin"
                val file2Name = manifest.file2Name.trim()

                // 1. Download or write turn on file 1
                if (manifest.turnOnFile1Url.isNotBlank()) {
                    onProgress("Downloading ON script...", 20)
                    val f = File(onDir, file1Name)
                    downloadFile(manifest.turnOnFile1Url, f)
                } else {
                    onProgress("Generating active boost script...", 25)
                    val f = File(onDir, file1Name)
                    f.writeText("# Xcel Game Booster Active Config\ngame_fps=120\nperformance_mode=high\ngpu_boost=1\ncache_trim=aggressive\n")
                }

                // 2. Download or write turn off file 1
                if (manifest.turnOffFile1Url.isNotBlank()) {
                    onProgress("Downloading OFF script...", 50)
                    val f = File(offDir, file1Name)
                    downloadFile(manifest.turnOffFile1Url, f)
                } else {
                    onProgress("Generating standard restore script...", 50)
                    val f = File(offDir, file1Name)
                    f.writeText("# Xcel Game Booster Default Config\ngame_fps=60\nperformance_mode=default\ngpu_boost=0\ncache_trim=normal\n")
                }

                // 3. Download turn on file 2 (if configured)
                if (manifest.turnOnFile2Url.isNotBlank() && file2Name.isNotBlank()) {
                    onProgress("Downloading secondary ON script...", 75)
                    val f = File(onDir, file2Name)
                    downloadFile(manifest.turnOnFile2Url, f)
                }

                // 4. Download turn off file 2 (if configured)
                if (manifest.turnOffFile2Url.isNotBlank() && file2Name.isNotBlank()) {
                    onProgress("Downloading secondary OFF script...", 90)
                    val f = File(offDir, file2Name)
                    downloadFile(manifest.turnOffFile2Url, f)
                }

                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_IS_STAGED, true)
                    .putString("file1_name", file1Name)
                    .putString("file2_name", file2Name)
                    .apply()

                onProgress("Completed!", 100)
                onComplete(true, null)
            } catch (e: Exception) {
                lastLog = "Stage download failed: ${e.message}"
                onComplete(false, e.message)
            }
        }.start()
    }

    private fun downloadFile(url: String, dest: File) {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code} downloading $url")
        val body = response.body ?: throw Exception("Empty body downloading $url")
        body.byteStream().use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun applyTurnOn(context: Context): Pair<Boolean, String> {
        val f1Name = getEffectiveFile1Name(context)
        val f2Name = getFile2Name(context)
        val (p1, p2) = getTargetPaths(context)

        val src1 = File(getTurnOnDir(context), f1Name)
        if (!src1.exists() || src1.length() == 0L) {
            src1.parentFile?.mkdirs()
            src1.writeText("# Xcel Game Booster Active Config\ngame_fps=120\nperformance_mode=high\ngpu_boost=1\ncache_trim=aggressive\n")
        }
        val src2 = if (f2Name.isNotEmpty()) File(getTurnOnDir(context), f2Name) else null

        val ok = copyStagedFile(src1, p1) && (src2 == null || copyStagedFile(src2, p2))
        if (ok) {
            setModState(context, "ON")
            // Apply background battery & cache optimization via Shizuku
            if (RenameUtil.useShizukuOps && RenameUtil.shizukuAvailable()) {
                RenameUtil.executeShizukuCommand("cmd deviceidle whitelist +com.dts.freefiremax; pm trim-caches 999999G")
            }
            return Pair(true, "Applied ON files successfully.")
        }
        return Pair(false, lastLog.ifEmpty { "Failed to copy ON files: ${RenameUtil.lastError}" })
    }

    fun applyTurnOff(context: Context): Pair<Boolean, String> {
        val f1Name = getEffectiveFile1Name(context)
        val f2Name = getFile2Name(context)
        val (p1, p2) = getTargetPaths(context)

        val src1 = File(getTurnOffDir(context), f1Name)
        if (!src1.exists() || src1.length() == 0L) {
            src1.parentFile?.mkdirs()
            src1.writeText("# Xcel Game Booster Default Config\ngame_fps=60\nperformance_mode=default\ngpu_boost=0\ncache_trim=normal\n")
        }
        val src2 = if (f2Name.isNotEmpty()) File(getTurnOffDir(context), f2Name) else null

        val ok = copyStagedFile(src1, p1) && (src2 == null || copyStagedFile(src2, p2))
        if (ok) {
            setModState(context, "OFF")
            return Pair(true, "Applied OFF files successfully.")
        }
        return Pair(false, lastLog.ifEmpty { "Failed to copy OFF files: ${RenameUtil.lastError}" })
    }

    private fun copyStagedFile(src: File, destPath: String): Boolean {
        if (!src.exists()) {
            lastLog = "Source file ${src.name} does not exist in staging."
            return false
        }
        val destFile = File(destPath)
        val destParent = destFile.parentFile
        if (destParent != null && !destParent.exists()) {
            try {
                destParent.mkdirs()
            } catch (_: Exception) {}
        }

        if (RenameUtil.useShizukuOps && RenameUtil.shizukuAvailable()) {
            val ok = RenameUtil.copyFileWithShizuku(src, destPath)
            if (ok) {
                // Also mirror to Download folder for system-level persistence and user transparency
                try {
                    val downloadMirror = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "XcelPanel/active_${src.name}")
                    downloadMirror.parentFile?.mkdirs()
                    RenameUtil.copyFileWithShizuku(src, downloadMirror.absolutePath)
                } catch (_: Exception) {}
                return true
            }
            lastLog = "Shizuku copy failed: ${RenameUtil.lastError}"
            // Fallback to standard copy if Shizuku failed
            return try {
                src.copyTo(destFile, overwrite = true)
                true
            } catch (e: Exception) {
                lastLog = "Copy failed: ${RenameUtil.lastError} | Standard: ${e.message}"
                false
            }
        }

        return try {
            src.copyTo(destFile, overwrite = true)
            true
        } catch (e: Exception) {
            lastLog = "Copy failed: ${e.message}"
            false
        }
    }
}
