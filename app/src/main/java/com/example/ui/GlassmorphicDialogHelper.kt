package com.example.ui

import android.app.Activity
import android.content.DialogInterface
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.R
import com.example.ui.theme.MyApplicationTheme

object GlassmorphicDialogHelper {

    @JvmStatic
    @JvmOverloads
    fun showPermissionPrompt(
        activity: Activity,
        type: PermissionPromptType,
        onPrimary: () -> Unit,
        onSecondary: (() -> Unit)? = null
    ) {
        val config = PermissionPromptConfigs.get(type)

        try {
            val overlay = activity.findViewById<ComposeView?>(R.id.compose_permission_overlay)
            if (overlay != null) {
                overlay.visibility = View.VISIBLE
                overlay.setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
                overlay.setContent {
                    MyApplicationTheme {
                        GlassmorphicPermissionDialog(
                            config = config,
                            onPrimary = {
                                dismissPermissionOverlay(activity)
                                onPrimary()
                            },
                            onSecondary = {
                                dismissPermissionOverlay(activity)
                                onSecondary?.invoke()
                            }
                        )
                    }
                }
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to standard dialog if overlay container is not in current view hierarchy or fails
        val builder = AlertDialog.Builder(activity, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dialog)
            .setTitle(config.title)
            .setMessage(config.description)
            .setCancelable(false)
            .setPositiveButton(config.primaryButtonText) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                onPrimary()
            }

        val secondaryText = config.secondaryButtonText
        if (!secondaryText.isNullOrEmpty()) {
            builder.setNegativeButton(secondaryText) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                onSecondary?.invoke()
            }
        }

        builder.show()
    }

    @JvmStatic
    fun dismissPermissionOverlay(activity: Activity) {
        activity.findViewById<ComposeView?>(R.id.compose_permission_overlay)?.let {
            it.visibility = View.GONE
        }
    }
}

