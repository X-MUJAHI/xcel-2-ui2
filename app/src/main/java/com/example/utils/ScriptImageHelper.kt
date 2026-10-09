package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.example.R

object ScriptImageHelper {

    /**
     * Attempts to find and decode the image for a script from res/raw/script<id>.jpg
     * Fallbacks to res/raw/script_<id>, res/drawable/script<id>, etc.
     */
    fun loadScriptBitmap(context: Context, scriptId: Int): Bitmap? {
        val candidates = listOf(
            "script$scriptId",
            "script_$scriptId",
            "script${scriptId}_img"
        )

        for (name in candidates) {
            // Check in raw
            var resId = context.resources.getIdentifier(name, "raw", context.packageName)
            if (resId == 0) {
                // Check in drawable
                resId = context.resources.getIdentifier(name, "drawable", context.packageName)
            }
            if (resId != 0) {
                try {
                    context.resources.openRawResource(resId).use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) return bmp
                    }
                } catch (e: Exception) {
                    // Try next candidate
                }
            }
        }
        return null
    }

    /**
     * Binds script image to ImageView. If image is found in res/raw/, sets bitmap.
     * Otherwise, sets cyber placeholder.
     */
    fun bindScriptImage(imageView: ImageView, scriptId: Int) {
        val bitmap = loadScriptBitmap(imageView.context, scriptId)
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            imageView.setImageResource(R.drawable.ic_script_placeholder)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }
}
