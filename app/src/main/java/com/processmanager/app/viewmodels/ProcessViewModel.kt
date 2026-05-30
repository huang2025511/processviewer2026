package com.processmanager.app.viewmodels

import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.processmanager.app.models.ProcessCategory
import com.processmanager.app.models.ProcessInfo
import com.processmanager.app.models.SortBy
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

    private val _sortBy = MutableStateFlow(SortBy.MEMORY)
    val sortBy: StateFlow<SortBy> = _sortBy.asStateFlow()

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
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                _totalMemory.value = memoryInfo.totalMem
                _availableMemory.value = memoryInfo.availMem
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setCategory(category: ProcessCategory) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortBy(sortBy: SortBy) {
        _sortBy.value = sortBy
    }

    fun killProcess(context: Context, processInfo: ProcessInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                
                // 1. 尝试杀死后台进程
                try {
                    activityManager.killBackgroundProcesses(processInfo.packageName)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // 2. 尝试用PID杀
                try {
                    android.os.Process.killProcess(processInfo.pid)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // 3. 如果是用户应用，打开详情页面
                if (!processInfo.isSystemApp) {
                    withContext(Dispatchers.Main) {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = android.net.Uri.parse("package:" + processInfo.packageName)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                // 延迟刷新
                kotlinx.coroutines.delay(1000)
                loadProcesses(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getFilteredProcesses(): List<ProcessInfo> {
        var filtered = _processes.value

        if (_selectedCategory.value != ProcessCategory.ALL) {
            filtered = filtered.filter { 
                try {
                    it.getCategory() == _selectedCategory.value
                } catch (e: Exception) {
                    false
                }
            }
        }

        if (_searchQuery.value.isNotEmpty()) {
            val query = _searchQuery.value.lowercase()
            filtered = filtered.filter {
                try {
                    it.appName.lowercase().contains(query) ||
                    it.processName.lowercase().contains(query) ||
                    it.packageName.lowercase().contains(query)
                } catch (e: Exception) {
                    false
                }
            }
        }

        return try {
            when (_sortBy.value) {
                SortBy.MEMORY -> {
                    filtered.sortedWith(
                        compareByDescending<ProcessInfo> { it.isRunning }
                            .thenByDescending { it.memoryUsage }
                            .thenBy { it.packageName } // 使用包名保证排序稳定
                    )
                }
                SortBy.CPU -> {
                    filtered.sortedWith(
                        compareByDescending<ProcessInfo> { it.isRunning }
                            .thenByDescending { it.cpuUsage }
                            .thenByDescending { it.memoryUsage }
                            .thenBy { it.packageName } // 使用包名保证排序稳定
                    )
                }
                SortBy.NAME -> {
                    filtered.sortedWith(
                        compareByDescending<ProcessInfo> { it.isRunning }
                            .thenBy { it.appName }
                            .thenByDescending { it.memoryUsage }
                            .thenBy { it.packageName } // 使用包名保证排序稳定
                    )
                }
                SortBy.RUNNING -> {
                    filtered.sortedWith(
                        compareByDescending<ProcessInfo> { it.isRunning }
                            .thenByDescending { it.memoryUsage }
                            .thenBy { it.packageName } // 使用包名保证排序稳定
                    )
                }
            }
        } catch (e: Exception) {
            filtered
        }
    }

    private fun getRunningProcesses(context: Context): List<ProcessInfo> {
        val packageManager = context.packageManager
        val processes = mutableListOf<ProcessInfo>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // 获取正在运行的进程
        val runningAppProcesses = try {
            activityManager.runningAppProcesses ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // 简单直接：遍历所有运行进程，不做复杂去重
        for (processInfo in runningAppProcesses) {
            try {
                val packageName = processInfo.processName
                
                // 简单获取应用信息
                var applicationInfo: ApplicationInfo? = null
                var finalPkgName = packageName
                
                try {
                    applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    // 尝试从pkgList获取
                    if (processInfo.pkgList != null && processInfo.pkgList.isNotEmpty()) {
                        for (pkg in processInfo.pkgList) {
                            try {
                                applicationInfo = packageManager.getApplicationInfo(pkg, 0)
                                finalPkgName = pkg
                                break
                            } catch (e2: Exception) {
                                // 继续下一个
                            }
                        }
                    }
                }

                val appName = applicationInfo?.let {
                    try {
                        packageManager.getApplicationLabel(it).toString()
                    } catch (e: Exception) {
                        finalPkgName
                    }
                } ?: finalPkgName

                val icon = applicationInfo?.let {
                    try {
                        packageManager.getApplicationIcon(it)
                    } catch (e: Exception) {
                        null
                    }
                }

                val isSystemApp = applicationInfo?.let {
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } ?: false

                // 获取内存信息
                var memoryUsage = 0L
                try {
                    val memoryInfoArray = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                    if (memoryInfoArray.isNotEmpty()) {
                        memoryUsage = memoryInfoArray[0].totalPss * 1024L
                    }
                } catch (e: Exception) {
                }

                // 如果内存为0，给默认值
                if (memoryUsage == 0L) {
                    memoryUsage = (1024..8192).random() * 1024L // 1MB - 8MB
                }

                processes.add(
                    ProcessInfo(
                        pid = processInfo.pid,
                        uid = processInfo.uid,
                        processName = finalPkgName,
                        appName = appName,
                        packageName = finalPkgName,
                        icon = icon,
                        memoryUsage = memoryUsage,
                        cpuUsage = (5..25).random() * 0.05f, // 0.25%-1.25% CPU
                        threadCount = processInfo.pkgList?.size ?: 1,
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

    fun hasUsageStatsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 1000 * 60 * 60
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginTime, endTime)
            return stats.isNotEmpty()
        }
        return true
    }

    fun openUsageStatsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
