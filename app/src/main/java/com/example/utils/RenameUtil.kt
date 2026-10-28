package com.example.utils

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import com.example.MainActivity
import java.io.File

object RenameUtil {
    var useShizukuOps: Boolean = true
    var lastError: String = ""

    fun shizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun executeShizukuCommand(command: String): Boolean {
        if (!shizukuAvailable()) {
            lastError = "Shizuku not available or permission denied"
            return false
        }
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess

            val errBuilder = StringBuilder()
            val errThread = Thread {
                try {
                    val errReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        errBuilder.append(line).append("\n")
                    }
                } catch (ignored: Exception) {}
            }
            errThread.start()

            val exitCode = process.waitFor()
            errThread.join(2000)

            if (exitCode != 0) {
                lastError = errBuilder.toString().trim()
                if (lastError.isEmpty()) {
                    lastError = "Command exited with code $exitCode"
                }
                android.util.Log.e("RenameUtil", "Command failed ($exitCode): $command\nError: $lastError")
                false
            } else {
                lastError = ""
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = e.message ?: "Unknown exception"
            false
        }
    }

    fun executeShizukuScriptAsync(script: String, onProgress: (String) -> Unit, onComplete: (Boolean) -> Unit) {
        if (!shizukuAvailable()) {
            onProgress("STATUS:Shizuku not available")
            onComplete(false)
            return
        }
        Thread {
            try {
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply {
                    isAccessible = true
                }
                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("sh", "-c", script),
                    null,
                    null
                ) as rikka.shizuku.ShizukuRemoteProcess
                
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                val errReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
                
                val errThread = Thread {
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        onProgress(line!!)
                    }
                }
                errThread.start()
                
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    onProgress(line!!)
                }
                
                process.waitFor()
                errThread.join()
                onComplete(process.exitValue() == 0)
            } catch (e: Exception) {
                e.printStackTrace()
                onProgress("STATUS:Error: ${e.message}")
                onComplete(false)
            }
        }.start()
    }

    fun executeShizukuCommandWithOutput(command: String): String {
        if (!shizukuAvailable()) {
            return "Shizuku not available"
        }
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess
            
            val output = StringBuilder()
            val errOutput = StringBuilder()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val errReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))

            val errThread = Thread {
                try {
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        errOutput.append(line).append("\n")
                    }
                } catch (ignored: Exception) {}
            }
            errThread.start()

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            process.waitFor()
            errThread.join(2000)
            if (output.isNotEmpty()) output.toString() else errOutput.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message}"
        }
    }


    fun checkDirExists(path: String): Boolean {
        if (!useShizukuOps || !shizukuAvailable()) {
            return File(path).isDirectory
        }
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", "[ -d \"$path\" ]"),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess
            process.waitFor() == 0
        } catch (e: Exception) {
            File(path).exists()
        }
    }

    fun checkFileExists(path: String): Boolean {
        if (!useShizukuOps || !shizukuAvailable()) {
            return File(path).isFile
        }
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", "[ -f \"$path\" ]"),
                null,
                null
            ) as rikka.shizuku.ShizukuRemoteProcess
            process.waitFor() == 0
        } catch (e: Exception) {
            File(path).exists()
        }
    }

    var appContext: android.content.Context? = null

    fun isModOn(): Boolean {
        val ctx = appContext ?: return false
        return PanelFileManager.isModOn(ctx)
    }

    fun turnOn(context: android.content.Context? = null): Boolean {
        val ctx = context ?: appContext
        if (ctx != null) {
            val result = PanelFileManager.applyTurnOn(ctx)
            lastError = if (result.first) "" else result.second
            return result.first
        }
        lastError = "Application context not available"
        return false
    }

    fun turnOff(context: android.content.Context? = null): String {
        val ctx = context ?: appContext
        if (ctx != null) {
            val result = PanelFileManager.applyTurnOff(ctx)
            lastError = if (result.first) "" else result.second
            return if (result.first) "SUCCESS" else if (result.second.contains("not exist", ignoreCase = true)) "DIR_NOT_FOUND" else "ERROR"
        }
        lastError = "Application context not available"
        return "ERROR"
    }

    fun copyDirectory(source: File, target: File): Boolean {
        val srcPath = source.absolutePath
        val destPath = target.absolutePath
        if (useShizukuOps && shizukuAvailable()) {
            val cmd = """
                rm -rf "$destPath" 2>/dev/null
                mkdir -p "$destPath"
                if ! cp -pr "$srcPath/." "$destPath/"; then
                    cp -r "$srcPath/." "$destPath/"
                fi
            """.trimIndent()
            return executeShizukuCommand(cmd)
        } else {
            // fallback (will likely fail on Android 11+ but keeps compilation and structure)
            if (!target.exists()) target.mkdirs()
            source.listFiles()?.forEach { file ->
                val dest = File(target, file.name)
                if (file.isDirectory) {
                    copyDirectory(file, dest)
                } else {
                    file.copyTo(dest, overwrite = true)
                }
            }
            return target.exists()
        }
    }

    fun extractZipToDirectoryMerge(zipFile: File, targetDir: File) {
        val zipPath = zipFile.absolutePath
        val destPath = targetDir.absolutePath
        if (useShizukuOps && shizukuAvailable()) {
            val cmd = "unzip -o \"$zipPath\" -d \"$destPath\""
            executeShizukuCommand(cmd)
        } else {
            // fallback (will likely fail on Android 11+ but fallback exists)
            java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile)).use { zis ->
                var entry: java.util.zip.ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        java.io.FileOutputStream(entryFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }

    fun getGameUIDs(): List<String> {
        val uids = mutableListOf<String>()
        val dirs = listOf(
            "/storage/emulated/0/Android/data/com.dts.freefiremax/files/Workshop",
            "/storage/emulated/0/Android/data/com.garena.game.ffi/files/Workshop"
        )
        if (useShizukuOps && shizukuAvailable()) {
            for (dir in dirs) {
                val output = executeShizukuCommandWithOutput("ls '$dir' 2>/dev/null")
                val lines = output.split("\n")
                for (line in lines) {
                    val name = line.trim()
                    if (name.isNotEmpty() && name.all { it.isDigit() }) {
                        uids.add(name)
                    }
                }
            }
        } else {
            for (dir in dirs) {
                File(dir).listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name.all { it.isDigit() }) {
                        uids.add(file.name)
                    }
                }
            }
        }
        return uids.distinct()
    }

    fun installNewScript(zipFile: File, onProgress: (String) -> Unit, onComplete: (Boolean) -> Unit) {
        val destDir = File("/storage/emulated/0/Android/data")
        val optionalDir = File("/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/Optional")
        val aDir = File(optionalDir, "android")
        val aData = File(optionalDir, "android-data")

        if (useShizukuOps && shizukuAvailable()) {
            val script = """
                #!/system/bin/sh
                ZIP_FILE="${zipFile.absolutePath}"
                DEST_DIR="${destDir.absolutePath}"
                OPTIONAL_DIR="${optionalDir.absolutePath}"
                A_DIR="${aDir.absolutePath}"
                A_DATA="${aData.absolutePath}"
                
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
                
                echo "STATUS:Extracting ${zipFile.name}..."
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
                    if (!zipFile.exists()) {
                        onProgress("STATUS:Error: Zip file not found")
                        onComplete(false)
                        return@Thread
                    }
                    onProgress("STATUS:Copying android backup...")
                    if (!aData.exists()) {
                        copyDirectory(aDir, aData)
                    }
                    onProgress("STATUS:Extracting ${zipFile.name}...")
                    extractZipToDirectoryMerge(zipFile, destDir)
                    zipFile.renameTo(File("/storage/emulated/0/Download/xcel1-used-delete-it.zip"))
                    onProgress("STATUS:Done!")
                    onComplete(true)
                } catch (e: Exception) {
                    onProgress("STATUS:Error: ${e.message}")
                    onComplete(false)
                }
            }.start()
        }
    }

    fun suspendPackage(pkg: String): Boolean {
        if (!useShizukuOps || !shizukuAvailable()) return false
        val cmd = "pm suspend --user 0 \"$pkg\" 2>/dev/null || pm suspend \"$pkg\""
        return executeShizukuCommand(cmd)
    }

    fun unsuspendPackage(pkg: String): Boolean {
        if (!useShizukuOps || !shizukuAvailable()) return false
        val cmd = "pm unsuspend --user 0 \"$pkg\" 2>/dev/null || pm unsuspend \"$pkg\""
        return executeShizukuCommand(cmd)
    }
}
