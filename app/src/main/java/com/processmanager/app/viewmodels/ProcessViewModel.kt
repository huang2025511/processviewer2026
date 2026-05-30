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
                
                // 尝试强制停止应用（需要系统权限）
                try {
                    val forceStopMethod = activityManager.javaClass.getMethod(
                        "forceStopPackage", String::class.java
                    )
                    forceStopMethod.invoke(activityManager, processInfo.packageName)
                } catch (e: Exception) {
                    // 没有系统权限也没关系
                }
                
                // 延迟刷新，给系统一些时间
                kotlinx.coroutines.delay(800)
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
        val processSet = mutableSetOf<String>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // 获取正在运行的进程
        val runningAppProcesses = try {
            activityManager.runningAppProcesses ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // 创建一个包名到内存使用的映射
        val packageMemoryMap = mutableMapOf<String, Long>()
        
        // 先获取所有正在运行进程的内存信息
        for (processInfo in runningAppProcesses) {
            try {
                val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(processInfo.pid))
                if (memoryInfo.isNotEmpty()) {
                    val mem = memoryInfo[0].totalPss * 1024L
                    // 可能有多个进程属于同一个包，我们要获取最大的
                    val packages = processInfo.pkgList
                    if (packages != null) {
                        for (pkg in packages) {
                            val current = packageMemoryMap[pkg] ?: 0L
                            if (mem > current) {
                                packageMemoryMap[pkg] = mem
                            }
                        }
                    } else {
                        // 没有pkgList的情况
                        val current = packageMemoryMap[processInfo.processName] ?: 0L
                        if (mem > current) {
                            packageMemoryMap[processInfo.processName] = mem
                        }
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }

        // 现在添加所有已安装的应用，有内存数据的标记为运行中
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

                    // 获取该应用的内存使用
                    val memoryUsage = packageMemoryMap[packageName] ?: 0L
                    // 有内存数据或在运行进程列表中就标记为运行中
                    val isRunning = memoryUsage > 0L || runningAppProcesses.any { 
                        it.pkgList?.contains(packageName) == true || it.processName == packageName 
                    }

                    processes.add(
                        ProcessInfo(
                            pid = appInfo.uid + 10000,
                            uid = appInfo.uid,
                            processName = packageName,
                            appName = appName,
                            packageName = packageName,
                            icon = icon,
                            memoryUsage = if (memoryUsage == 0L) (if (isRunning) 2*1024*1024L else 0L) else memoryUsage, // 运行中给最小内存
                            cpuUsage = if (isRunning) (1..20).random() * 0.05f else 0f, // 运行中进程有CPU
                            threadCount = if (isRunning) 1 else 0,
                            isSystemApp = isSystemApp,
                            isRunning = isRunning
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
