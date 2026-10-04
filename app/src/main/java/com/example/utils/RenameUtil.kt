package com.example.utils

import android.content.Context
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object RenameUtil {
    private const val TAG = "RenameUtil"
    var appContext: Context? = null
    var useShizukuOps: Boolean = true
    var lastError: String = ""

    fun shizukuAvailable(): Boolean {
        return try {
            if (Shizuku.pingBinder()) {
                Shizuku.checkSelfPermission() == 0
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isModOn(): Boolean {
        val ctx = appContext ?: return false
        return PanelFileManager.isModOn(ctx)
    }

    fun turnOn(context: Context? = null): Boolean {
        val ctx = context ?: appContext
        if (ctx != null) {
            val (success, log) = PanelFileManager.applyTurnOn(ctx)
            lastError = if (success) "" else log
            return success
        }
        lastError = "Application context not available"
        return false
    }

    fun turnOff(context: Context? = null): String {
        val ctx = context ?: appContext
        if (ctx != null) {
            val (success, log) = PanelFileManager.applyTurnOff(ctx)
            lastError = if (success) "" else log
            return if (success) {
                "SUCCESS"
            } else {
                if (log.contains("not exist", ignoreCase = true)) "DIR_NOT_FOUND" else "ERROR"
            }
        }
        lastError = "Application context not available"
        return "ERROR"
    }

    fun executeShizukuCommand(cmd: String): Boolean {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as ShizukuRemoteProcess
            process.waitFor() == 0
        } catch (e: Exception) {
            lastError = e.message ?: "Shizuku exec failed"
            false
        }
    }

    fun executeShizukuCommandWithOutput(cmd: String): String {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as ShizukuRemoteProcess
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val sb = java.lang.StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            process.waitFor()
            sb.toString().trim()
        } catch (e: Exception) {
            lastError = e.message ?: "Shizuku exec failed"
            ""
        }
    }

    fun checkDirExists(path: String): Boolean {
        if (useShizukuOps && shizukuAvailable()) {
            val res = executeShizukuCommandWithOutput("[ -d '$path' ] && echo 'EXISTS'")
            return res.contains("EXISTS")
        }
        val f = File(path)
        return f.exists() && f.isDirectory
    }

    fun checkFileExists(path: String): Boolean {
        if (useShizukuOps && shizukuAvailable()) {
            val res = executeShizukuCommandWithOutput("[ -f '$path' ] && echo 'EXISTS'")
            return res.contains("EXISTS")
        }
        val f = File(path)
        return f.exists() && f.isFile
    }

    fun getGameUIDs(): List<String> {
        val uids = mutableListOf<String>()
        val paths = listOf(
            "/storage/emulated/0/Android/data/com.dts.freefiremax/files/Workshop",
            "/storage/emulated/0/Android/data/com.dts.freefireth/files/Workshop"
        )
        if (useShizukuOps && shizukuAvailable()) {
            for (p in paths) {
                val out = executeShizukuCommandWithOutput("ls '$p' 2>/dev/null")
                for (item in out.lines()) {
                    val trimmed = item.trim()
                    if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) {
                        uids.add(trimmed)
                    }
                }
            }
        } else {
            for (p in paths) {
                val files = File(p).listFiles() ?: continue
                for (f in files) {
                    if (f.isDirectory && f.name.all { it.isDigit() }) {
                        uids.add(f.name)
                    }
                }
            }
        }
        return uids.distinct()
    }

    fun installNewScript(
        zipFile: File,
        onProgress: (String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        val destDir = File("/storage/emulated/0/Android/data")
        val optionalDir = File("/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional")
        val aDir = File(optionalDir, "android")
        val aData = File(optionalDir, "android-data")

        if (useShizukuOps && shizukuAvailable()) {
            val zipPath = zipFile.absolutePath
            val destPath = destDir.absolutePath
            val optPath = optionalDir.absolutePath
            val aDirPath = aDir.absolutePath
            val aDataPath = aData.absolutePath
            val fileName = zipFile.name

            val script = """
                #!/system/bin/sh
                ZIP_FILE="$zipPath"
                DEST_DIR="$destPath"
                OPTIONAL_DIR="$optPath"
                A_DIR="$aDirPath"
                A_DATA="$aDataPath"
                
                if [ ! -f "${'$'}ZIP_FILE" ]; then
                    echo "STATUS:Error: Zip file not found" >&2
                    exit 1
                fi
                
                echo "STATUS:Copying android backup..."
                if [ ! -d "${'$'}A_DATA" ]; then
                    if ! cp -pr "${'$'}A_DIR" "${'$'}A_DATA"; then
                        mkdir -p "${'$'}A_DATA" && cp -r "${'$'}A_DIR/." "${'$'}A_DATA/"
                    fi
                fi
                
                echo "STATUS:Extracting $fileName..."
                if unzip -o -q "${'$'}ZIP_FILE" -d "${'$'}DEST_DIR" ; then
                    mv "${'$'}ZIP_FILE" "/storage/emulated/0/Download/xcel1-used-delete-it.zip"
                    echo "STATUS:Done!"
                else
                    echo "STATUS:Error: Unzip failed"
                    exit 1
                fi
            """.trimIndent()

            executeShizukuScriptAsync(script, onProgress, onComplete)
        } else {
            Thread {
                try {
                    onProgress("Copying backup...")
                    if (!aData.exists()) {
                        aDir.copyRecursively(aData, overwrite = true)
                    }
                    onProgress("Extracting files...")
                    unzip(zipFile, destDir)
                    zipFile.delete()
                    onComplete(true)
                } catch (e: Exception) {
                    lastError = e.message ?: "Install failed"
                    onComplete(false)
                }
            }.start()
        }
    }

    private fun executeShizukuScriptAsync(
        script: String,
        onProgress: (String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        Thread {
            try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(null, arrayOf("sh", "-c", script), null, null) as ShizukuRemoteProcess
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line?.trim() ?: ""
                    if (l.startsWith("STATUS:")) {
                        onProgress(l.removePrefix("STATUS:"))
                    }
                }
                reader.close()
                val code = process.waitFor()
                onComplete(code == 0)
            } catch (e: Exception) {
                lastError = e.message ?: "Script execution error"
                onComplete(false)
            }
        }.start()
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
