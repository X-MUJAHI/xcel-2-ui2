package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color

object PermissionPromptConfigs {
    fun get(type: PermissionPromptType): PermissionPromptConfig {
        return when (type) {
            PermissionPromptType.STORAGE_ALL_FILES -> PermissionPromptConfig(
                badge = "STORAGE PERMISSION",
                title = "All Files Access",
                description = "Xcel-2 requires Manage Storage permission to safely stage, backup, and restore required game modification files.",
                bullets = listOf(
                    "Staging turn-on & turn-off configuration files",
                    "Preserving original game data without corruption"
                ),
                icon = Icons.Filled.FolderOpen,
                iconTint = Color(0xFF00E5FF),
                primaryButtonText = "OPEN SETTINGS",
                secondaryButtonText = "CONTINUE ANYWAY"
            )
            PermissionPromptType.STORAGE_LEGACY -> PermissionPromptConfig(
                badge = "STORAGE ACCESS",
                title = "Read & Write Storage",
                description = "Allow storage permission to write and update game configuration files.",
                bullets = listOf(
                    "Required for active file swapping",
                    "Enables one-tap mod activation"
                ),
                icon = Icons.Filled.Folder,
                iconTint = Color(0xFF00E5FF),
                primaryButtonText = "GRANT PERMISSION",
                secondaryButtonText = "CONTINUE"
            )
            PermissionPromptType.STORAGE_DENIED -> PermissionPromptConfig(
                badge = "PERMISSION REQUIRED",
                title = "Storage Denied",
                description = "Storage access was denied. Without it, files cannot be staged or activated in the target game folders.",
                bullets = listOf(
                    "Tap Retry to authorize storage access",
                    "Or open App Settings manually"
                ),
                icon = Icons.Filled.Warning,
                iconTint = Color(0xFFFF5252),
                primaryButtonText = "RETRY PERMISSION",
                secondaryButtonText = "CONTINUE ANYWAY"
            )
            PermissionPromptType.USAGE_ACCESS -> PermissionPromptConfig(
                badge = "USAGE MONITOR",
                title = "Usage Access Required",
                description = "Please grant Usage Access permission to allow Xcel-2 to accurately detect Free Fire game state and optimize background services.",
                bullets = listOf(
                    "Monitors active game window",
                    "Auto-manages background resource boost"
                ),
                icon = Icons.Filled.Timer,
                iconTint = Color(0xFF00E5FF),
                primaryButtonText = "OPEN SETTINGS",
                secondaryButtonText = "CONTINUE ANYWAY"
            )
            PermissionPromptType.SHIZUKU_REQUIRED -> PermissionPromptConfig(
                badge = "SHIZUKU ACCESS",
                title = "Shizuku Permission",
                description = "Grant Shizuku permission to perform fast system operations without root.",
                bullets = listOf(
                    "Fast cache trimming and memory optimization",
                    "Direct file operations in Android/data"
                ),
                icon = Icons.Filled.Security,
                iconTint = Color(0xFF00E676),
                primaryButtonText = "GRANT ACCESS",
                secondaryButtonText = "SKIP"
            )
            PermissionPromptType.SYSTEM_ALERT_WINDOW -> PermissionPromptConfig(
                badge = "OVERLAY PERMISSION",
                title = "Display Over Other Apps",
                description = "Allow display over other apps to show gaming HUD and FPS performance overlay.",
                bullets = listOf(
                    "Floating game booster control panel",
                    "Real-time FPS and ping monitor"
                ),
                icon = Icons.Filled.Build,
                iconTint = Color(0xFFFFD740),
                primaryButtonText = "ENABLE OVERLAY",
                secondaryButtonText = "CANCEL"
            )
        }
    }
}
