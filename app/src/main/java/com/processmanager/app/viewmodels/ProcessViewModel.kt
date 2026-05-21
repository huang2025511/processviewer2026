package com.processmanager.app.viewmodels

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Debug
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.processmanager.app.models.ProcessCategory
import com.processmanager.app.models.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessViewModel : ViewModel() {
    private val _processes = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val processes: StateFlow<List<ProcessInfo>> = _processes.asStateFlow()

    private val _selectedCategory = MutableStateFlow(ProcessCategory.ALL)
    val selectedCategory: StateFlow<ProcessCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _totalMemory = MutableStateFlow(0L)
    val totalMemory: StateFlow<Long> = _totalMemory.asStateFlow()

    private val _availableMemory = MutableStateFlow(0L)
    val availableMemory: StateFlow<Long> = _availableMemory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadProcesses(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            val processList = withContext(Dispatchers.IO) {
                getRunningProcesses(context)
            }
            _processes.value = processList
            _isLoading.value = false
        }
    }

    fun loadMemoryInfo(context: Context) {
        viewModelScope.launch {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            _totalMemory.value = memoryInfo.totalMem
            _availableMemory.value = memoryInfo.availMem
        }
    }

    fun setCategory(category: ProcessCategory) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun killProcess(context: Context, processInfo: ProcessInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(processInfo.packageName)
            android.os.Process.killProcess(processInfo.pid)
            loadProcesses(context)
        }
    }

    fun getFilteredProcesses(): List<ProcessInfo> {
        var filtered = _processes.value

        if (_selectedCategory.value != ProcessCategory.ALL) {
            filtered = filtered.filter { it.getCategory() == _selectedCategory.value }
        }

        if (_searchQuery.value.isNotEmpty()) {
            val query = _searchQuery.value.lowercase()
            filtered = filtered.filter {
                it.appName.lowercase().contains(query) ||
                it.processName.lowercase().contains(query) ||
                it.packageName.lowercase().contains(query)
            }
        }

        return filtered.sortedWith(compareByDescending<ProcessInfo> { it.memoryUsage }
            .thenBy { it.appName })
    }

    private fun getRunningProcesses(context: Context): List<ProcessInfo> {
        val packageManager = context.packageManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = mutableListOf<ProcessInfo>()

        val runningAppProcesses = activityManager.runningAppProcesses ?: return emptyList()

        for (processInfo in runningAppProcesses) {
            try {
                val packageName = processInfo.processName
                val applicationInfo = try {
                    packageManager.getApplicationInfo(packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    val packages = packageManager.getPackagesForUid(processInfo.uid)
                    if (!packages.isNullOrEmpty()) {
                        packageManager.getApplicationInfo(packages[0], 0)
                    } else {
                        null
                    }
                }

                val appName = applicationInfo?.let {
                    packageManager.getApplicationLabel(it).toString()
                } ?: packageName

                val icon = applicationInfo?.let {
                    try {
                        packageManager.getApplicationIcon(it)
                    } catch (e: Exception) {
                        null
                    }
                }

                val isSystemApp = applicationInfo?.let {
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } ?: true

                val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                val memoryUsage = if (memoryInfo.isNotEmpty()) {
                    memoryInfo[0].totalPss * 1024L
                } else {
                    0L
                }

                processes.add(
                    ProcessInfo(
                        pid = processInfo.pid,
                        uid = processInfo.uid,
                        processName = packageName,
                        appName = appName,
                        packageName = applicationInfo?.packageName ?: packageName,
                        icon = icon,
                        memoryUsage = memoryUsage,
                        isSystemApp = isSystemApp,
                        isRunning = true
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return processes
    }

    fun formatMemory(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
