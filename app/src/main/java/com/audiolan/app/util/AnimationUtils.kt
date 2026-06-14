package com.audiolan.app.util

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object AnimationUtils {
    fun isSystemAnimationEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
        val accessibilityAnimationEnabled = runCatching {
            val method = AccessibilityManager::class.java.methods.firstOrNull { method ->
                method.name == "isAnimationEnabled" && method.parameterTypes.isEmpty()
            }
            method?.invoke(accessibilityManager) as? Boolean ?: true
        }.getOrDefault(true)

        val animatorScale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)

        return accessibilityAnimationEnabled && animatorScale != 0f
    }
}
