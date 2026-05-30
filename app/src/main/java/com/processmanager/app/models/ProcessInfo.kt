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
        return try {
            when {
                isSystemApp -> ProcessCategory.SYSTEM
                processName.contains(":") || processName.lowercase().contains("service") -> ProcessCategory.SERVICE
                else -> ProcessCategory.USER
            }
        } catch (e: Exception) {
            ProcessCategory.USER
        }
    }
}

enum class ProcessCategory {
    ALL,
    USER,
    SYSTEM,
    SERVICE
}

enum class SortBy {
    MEMORY,
    CPU,
    NAME,
    RUNNING
}
