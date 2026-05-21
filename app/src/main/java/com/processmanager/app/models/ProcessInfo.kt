package com.processmanager.app.models

import android.graphics.drawable.Drawable

data class ProcessInfo(
    val pid: Int,
    val uid: Int,
    val processName: String,
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    val memoryUsage: Long,
    val isSystemApp: Boolean,
    val isRunning: Boolean = true,
    val cpuUsage: Float = 0f,
    val threadCount: Int = 0
) {
    fun getCategory(): ProcessCategory {
        return when {
            isSystemApp -> ProcessCategory.SYSTEM
            processName.contains(":") || processName.lowercase().contains("service") -> ProcessCategory.SERVICE
            else -> ProcessCategory.USER
        }
    }
}

enum class ProcessCategory {
    ALL,
    USER,
    SYSTEM,
    SERVICE
}
