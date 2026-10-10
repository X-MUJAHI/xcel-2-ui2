package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.work.*
import java.io.File
import java.net.URL

class UpdateManager(private val context: Context) {

    fun startUpdate(apkUrl: String) {
        val data = workDataOf("URL" to apkUrl)
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "apk_download",
            ExistingWorkPolicy.REPLACE,
            downloadRequest
        )
    }

    class DownloadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            val apkUrl = inputData.getString("URL") ?: return Result.failure()
            val outputFile = File(applicationContext.cacheDir, "update.apk")

            try {
                URL(apkUrl).openStream().use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                installApk(outputFile)
                return Result.success()
            } catch (e: Exception) {
                return Result.failure()
            }
        }

        private fun installApk(file: File) {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(applicationContext, "${applicationContext.packageName}.provider", file)
            } else {
                Uri.fromFile(file)
            }
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            applicationContext.startActivity(intent)
        }
    }
}
