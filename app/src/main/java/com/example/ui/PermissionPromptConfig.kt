package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class PermissionPromptConfig(
    val badge: String,
    val title: String,
    val description: String,
    val bullets: List<String>,
    val icon: ImageVector,
    val iconTint: Color,
    val primaryButtonText: String,
    val secondaryButtonText: String? = null
)
