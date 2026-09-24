package com.example.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

enum class PermissionPromptType {
    STORAGE_ALL_FILES,
    STORAGE_LEGACY,
    STORAGE_DENIED,
    USAGE_ACCESS,
    SHIZUKU
}

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

object PermissionPromptConfigs {
    fun get(type: PermissionPromptType): PermissionPromptConfig {
        return when (type) {
            PermissionPromptType.STORAGE_ALL_FILES -> PermissionPromptConfig(
                badge = "STORAGE PERMISSION",
                title = "All Files Access",
                description = "X-Celestials requires Manage Storage permission to safely stage, backup, and restore required game modification files.",
                bullets = listOf(
                    "Staging turn-on & turn-off configuration files",
                    "Preserving original game data without corruption"
                ),
                icon = Icons.Default.Info,
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
                icon = Icons.Default.Info,
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
                icon = Icons.Default.Warning,
                iconTint = Color(0xFFFF5252),
                primaryButtonText = "RETRY PERMISSION",
                secondaryButtonText = "EXIT APP"
            )
            PermissionPromptType.USAGE_ACCESS -> PermissionPromptConfig(
                badge = "USAGE MONITOR",
                title = "Usage Access Required",
                description = "Please grant Usage Access permission to allow X-Celestials to accurately detect Free Fire game state and optimize background services.",
                bullets = listOf(
                    "Real-time game start & exit detection",
                    "Automatic session management"
                ),
                icon = Icons.Default.Refresh,
                iconTint = Color(0xFF00E5FF),
                primaryButtonText = "OPEN SETTINGS",
                secondaryButtonText = "CONTINUE ANYWAY"
            )
            PermissionPromptType.SHIZUKU -> PermissionPromptConfig(
                badge = "HIGH SPEED BOOST",
                title = "Shizuku Permission",
                description = "Authorize Shizuku service to enable instant cache cleaning, process trimming, and advanced system commands without root.",
                bullets = listOf(
                    "High-speed device boost and memory trimming",
                    "Elevated background operations"
                ),
                icon = Icons.Default.Check,
                iconTint = Color(0xFF7C4DFF),
                primaryButtonText = "GRANT ACCESS",
                secondaryButtonText = "SKIP FOR NOW"
            )
        }
    }
}

@Composable
fun GlassmorphicPermissionDialog(
    config: PermissionPromptConfig,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: (() -> Unit)? = null
) {
    // Pulsing glow animation for the ambient glass effect
    val infiniteTransition = rememberInfiniteTransition(label = "glassGlow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD00A0E17))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ambient background color orbs showing through the frosted glass
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = (-60).dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            config.iconTint.copy(alpha = 0.35f * pulseAlpha),
                            Color(0xFF7C4DFF).copy(alpha = 0.2f * pulseAlpha),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 70.dp, y = 90.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.25f * pulseAlpha),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // The Glassmorphic Frosted Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = config.iconTint.copy(alpha = 0.5f),
                    ambientColor = Color.Black
                )
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xEE161F2E),
                            Color(0xF50F1622),
                            Color(0xFA0B101A)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.15f),
                            config.iconTint.copy(alpha = 0.5f),
                            Color.White.copy(alpha = 0.25f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Circular Glowing Icon Container
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    config.iconTint.copy(alpha = 0.28f),
                                    Color(0x15FFFFFF)
                                )
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.6f),
                                    config.iconTint.copy(alpha = 0.5f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = config.icon,
                        contentDescription = config.title,
                        tint = config.iconTint,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Frosted Badge Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFFFFF))
                        .border(
                            width = 0.8.dp,
                            color = config.iconTint.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = config.badge,
                        color = config.iconTint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title
                Text(
                    text = config.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Description
                Text(
                    text = config.description,
                    color = Color(0xFFCFD8DC),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Inner Card for bullets
                if (config.bullets.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x18FFFFFF))
                            .border(
                                width = 0.8.dp,
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        config.bullets.forEach { bullet ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(config.iconTint)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = bullet,
                                    color = Color(0xFFECEFF1),
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))
                }

                // Action Buttons
                Button(
                    onClick = onPrimaryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(14.dp),
                            spotColor = config.iconTint.copy(alpha = 0.6f)
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = config.iconTint,
                        contentColor = Color(0xFF0F141C)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = config.primaryButtonText,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                if (onSecondaryClick != null && !config.secondaryButtonText.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x12FFFFFF))
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSecondaryClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = config.secondaryButtonText,
                            color = Color(0xFFB0BEC5),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

object GlassmorphicDialogHelper {
    fun showPermissionPrompt(
        activity: Activity,
        type: PermissionPromptType,
        onPrimary: () -> Unit,
        onSecondary: (() -> Unit)? = null
    ) {
        val config = PermissionPromptConfigs.get(type)

        // 1. Primary path: Use in-layout overlay ComposeView
        val overlayView = activity.findViewById<ComposeView>(com.example.R.id.compose_permission_overlay)
        if (overlayView != null) {
            overlayView.visibility = android.view.View.VISIBLE
            overlayView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            overlayView.setContent {
                MaterialTheme {
                    GlassmorphicPermissionDialog(
                        config = config,
                        onPrimaryClick = {
                            overlayView.visibility = android.view.View.GONE
                            onPrimary()
                        },
                        onSecondaryClick = onSecondary?.let { action ->
                            {
                                overlayView.visibility = android.view.View.GONE
                                action()
                            }
                        }
                    )
                }
            }
            return
        }

        // 2. Secondary fallback: ComponentDialog with explicit ViewTree owners
        try {
            val dialog = androidx.activity.ComponentDialog(activity)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            dialog.setCancelable(false)

            dialog.window?.decorView?.let { decor ->
                if (activity is androidx.lifecycle.LifecycleOwner) {
                    decor.setViewTreeLifecycleOwner(activity)
                }
                if (activity is androidx.lifecycle.ViewModelStoreOwner) {
                    decor.setViewTreeViewModelStoreOwner(activity)
                }
                if (activity is androidx.savedstate.SavedStateRegistryOwner) {
                    decor.setViewTreeSavedStateRegistryOwner(activity)
                }
            }

            val composeView = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    MaterialTheme {
                        GlassmorphicPermissionDialog(
                            config = config,
                            onPrimaryClick = {
                                dialog.dismiss()
                                onPrimary()
                            },
                            onSecondaryClick = onSecondary?.let { action ->
                                {
                                    dialog.dismiss()
                                    action()
                                }
                            }
                        )
                    }
                }
            }

            dialog.setContentView(composeView)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
            // 3. Fallback to standard dialog
            android.app.AlertDialog.Builder(activity)
                .setTitle(config.title)
                .setMessage(config.description)
                .setPositiveButton(config.primaryButtonText) { _, _ -> onPrimary() }
                .apply {
                    if (onSecondary != null && !config.secondaryButtonText.isNullOrEmpty()) {
                        setNegativeButton(config.secondaryButtonText) { _, _ -> onSecondary() }
                    }
                }
                .setCancelable(false)
                .show()
        }
    }

    fun dismissPermissionOverlay(activity: Activity) {
        activity.findViewById<ComposeView>(com.example.R.id.compose_permission_overlay)?.visibility = android.view.View.GONE
    }
}
