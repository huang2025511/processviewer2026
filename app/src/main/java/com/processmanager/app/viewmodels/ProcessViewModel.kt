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
                
                // 尝试杀死后台进程
                try {
                    activityManager.killBackgroundProcesses(processInfo.packageName)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                // 尝试强制停止应用
                try {
                    val forceStopMethod = activityManager.javaClass.getMethod(
                        "forceStopPackage", String::class.java
                    )
                    forceStopMethod.invoke(activityManager, processInfo.packageName)
                } catch (e: Exception) {
                    // 没有系统权限也没关系
                }
                
                // 延迟刷新，给系统一些时间
                kotlinx.coroutines.delay(500)
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
            filtered.sortedWith(compareByDescending<ProcessInfo> { it.memoryUsage }
                .thenBy { it.appName })
        } catch (e: Exception) {
            filtered
        }
    }

    private fun getRunningProcesses(context: Context): List<ProcessInfo> {
        val packageManager = context.packageManager
        val processes = mutableListOf<ProcessInfo>()
        val processSet = mutableSetOf<String>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // 方法1：使用 runningAppProcesses
        val runningAppProcesses = try {
            activityManager.runningAppProcesses ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        for (processInfo in runningAppProcesses) {
            try {
                val packageName = processInfo.processName
                if (processSet.contains(packageName)) continue

                val applicationInfo = try {
                    packageManager.getApplicationInfo(packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    val packages = try {
                        packageManager.getPackagesForUid(processInfo.uid)
                    } catch (e: Exception) {
                        null
                    }
                    if (!packages.isNullOrEmpty()) {
                        try {
                            packageManager.getApplicationInfo(packages[0], 0)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }

                val appName = applicationInfo?.let {
                    try {
                        packageManager.getApplicationLabel(it).toString()
                    } catch (e: Exception) {
                        packageName
                    }
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

                val memoryUsage = try {
                    val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                    if (memoryInfo.isNotEmpty()) {
                        memoryInfo[0].totalPss * 1024L
                    } else {
                        0L
                    }
                } catch (e: Exception) {
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
                processSet.add(packageName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 方法2：从已安装应用中获取更多进程信息（改进方法）
        try {
            val installedPackages = packageManager.getInstalledApplications(0)
            for (appInfo in installedPackages) {
                try {
                    val packageName = appInfo.packageName
                    if (processSet.contains(packageName)) continue

                    val appName = try {
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        packageName
                    }

                    val icon = try {
                        packageManager.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }

                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    // 尝试获取内存信息
                    val memoryUsage = try {
                        val processesForPkg = runningAppProcesses.filter { 
                            it.processName == packageName || it.pkgList?.contains(packageName) == true
                        }
                        if (processesForPkg.isNotEmpty()) {
                            val pids = processesForPkg.map { it.pid }.toIntArray()
                            val memoryInfo = activityManager.getProcessMemoryInfo(pids)
                            memoryInfo.sumOf { it.totalPss } * 1024L
                        } else {
                            0L
                        }
                    } catch (e: Exception) {
                        0L
                    }

                    processes.add(
                        ProcessInfo(
                            pid = appInfo.uid + 10000,
                            uid = appInfo.uid,
                            processName = packageName,
                            appName = appName,
                            packageName = packageName,
                            icon = icon,
                            memoryUsage = memoryUsage,
                            isSystemApp = isSystemApp,
                            isRunning = memoryUsage > 0 // 有内存占用视为正在运行
                        )
                    )
                    processSet.add(packageName)
                } catch (e: Exception) {
                    continue
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
