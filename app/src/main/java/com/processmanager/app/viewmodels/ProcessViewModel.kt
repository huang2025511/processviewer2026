package com.processmanager.app.viewmodels

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.processmanager.app.models.ProcessCategory
import com.processmanager.app.models.ProcessInfo
import com.processmanager.app.models.SortBy
import com.processmanager.app.utils.CpuMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessViewModel : ViewModel() {
    private val _processes = MutableStateFlow&lt;List&lt;ProcessInfo&gt;&gt;(emptyList())
    val processes: StateFlow&lt;List&lt;ProcessInfo&gt;&gt; = _processes.asStateFlow()

    private val _selectedCategory = MutableStateFlow(ProcessCategory.ALL)
    val selectedCategory: StateFlow&lt;ProcessCategory&gt; = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow&lt;String&gt; = _searchQuery.asStateFlow()

    private val _totalMemory = MutableStateFlow(0L)
    val totalMemory: StateFlow&lt;Long&gt; = _totalMemory.asStateFlow()

    private val _availableMemory = MutableStateFlow(0L)
    val availableMemory: StateFlow&lt;Long&gt; = _availableMemory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow&lt;Boolean&gt; = _isLoading.asStateFlow()

    private val _sortBy = MutableStateFlow(SortBy.MEMORY)
    val sortBy: StateFlow&lt;SortBy&gt; = _sortBy.asStateFlow()
    
    private var isMonitoring = false
    private val cpuMonitor = CpuMonitor()

    fun loadProcesses(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            val processList = withContext(Dispatchers.IO) {
                getRunningProcesses(context)
            }
            _processes.value = processList
            _isLoading.value = false
            
            startRealtimeMonitoring(context)
        }
    }
    
    private fun startRealtimeMonitoring(context: Context) {
        if (isMonitoring) return
        isMonitoring = true
        
        viewModelScope.launch {
            while (isActive) {
                delay(2000)
                updateRealtimeData(context)
            }
        }
    }
    
    private suspend fun updateRealtimeData(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val updatedProcesses = _processes.value.map { process -&gt;
                    var newMemoryUsage = process.memoryUsage
                    var newCpuUsage = process.cpuUsage
                    
                    try {
                        val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(process.pid))
                        if (memoryInfo.isNotEmpty() &amp;&amp; memoryInfo[0].totalPss &gt; 0) {
                            newMemoryUsage = memoryInfo[0].totalPss * 1024L
                        }
                    } catch (e: Exception) {
                        // 忽略
                    }
                    
                    try {
                        val cpuUsage = cpuMonitor.getCpuUsage(process.pid)
                        newCpuUsage = cpuUsage.processCpuUsage
                    } catch (e: Exception) {
                        // 忽略
                    }
                    
                    process.copy(
                        memoryUsage = newMemoryUsage,
                        cpuUsage = newCpuUsage
                    )
                }
                
                _processes.value = updatedProcesses
                
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                _totalMemory.value = memoryInfo.totalMem
                _availableMemory.value = memoryInfo.availMem
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                
                try {
                    activityManager.killBackgroundProcesses(processInfo.packageName)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                try {
                    if (processInfo.packageName == context.packageName) {
                        android.os.Process.killProcess(processInfo.pid)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                kotlinx.coroutines.delay(1000)
                loadProcesses(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getFilteredProcesses(): List&lt;ProcessInfo&gt; {
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
                SortBy.MEMORY -&gt; {
                    filtered.sortedWith(
                        compareByDescending&lt;ProcessInfo&gt; { it.isRunning }
                            .thenByDescending { it.memoryUsage }
                            .thenBy { it.packageName }
                    )
                }
                SortBy.CPU -&gt; {
                    filtered.sortedWith(
                        compareByDescending&lt;ProcessInfo&gt; { it.isRunning }
                            .thenByDescending { it.cpuUsage }
                            .thenByDescending { it.memoryUsage }
                            .thenBy { it.packageName }
                    )
                }
                SortBy.NAME -&gt; {
                    filtered.sortedWith(
                        compareByDescending&lt;ProcessInfo&gt; { it.isRunning }
                            .thenBy { it.appName }
                            .thenByDescending { it.memoryUsage }
                            .thenBy { it.packageName }
                    )
                }
                SortBy.RUNNING -&gt; {
                    filtered.sortedWith(
                        compareByDescending&lt;ProcessInfo&gt; { it.isRunning }
                            .thenByDescending { it.memoryUsage }
                            .thenBy { it.packageName }
                    )
                }
            }
        } catch (e: Exception) {
            filtered
        }
    }

    private fun getRunningProcesses(context: Context): List&lt;ProcessInfo&gt; {
        val packageManager = context.packageManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val realProcesses = mutableListOf&lt;ProcessInfo&gt;()

        // 从 runningAppProcesses 中获取真实进程
        try {
            val runningAppProcesses = activityManager.runningAppProcesses ?: emptyList()
            for (process in runningAppProcesses) {
                try {
                    val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(process.pid))
                    var memory = 0L
                    if (memoryInfo.isNotEmpty()) {
                        memory = memoryInfo[0].totalPss * 1024L
                    }

                    val packages = process.pkgList
                    val packageName = if (packages != null &amp;&amp; packages.isNotEmpty()) {
                        packages[0]
                    } else {
                        process.processName
                    }

                    // 获取应用信息
                    val appInfo = try {
                        packageManager.getApplicationInfo(packageName, 0)
                    } catch (e: Exception) {
                        continue
                    }

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

                    // 获取真实CPU使用率
                    val cpuUsage = try {
                        cpuMonitor.getCpuUsage(process.pid).processCpuUsage
                    } catch (e: Exception) {
                        0f
                    }

                    realProcesses.add(
                        ProcessInfo(
                            pid = process.pid,
                            uid = appInfo.uid,
                            processName = process.processName,
                            appName = appName,
                            packageName = packageName,
                            icon = icon,
                            memoryUsage = memory,
                            cpuUsage = cpuUsage,
                            threadCount = 1,
                            isSystemApp = isSystemApp,
                            isRunning = true
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 从 runningServices 中获取更多真实进程
        try {
            val runningServices = activityManager.getRunningServices(200) ?: emptyList()
            for (service in runningServices) {
                try {
                    val pkgName = service.service.packageName
                    val pid = service.pid

                    // 检查是否已经添加过
                    val alreadyAdded = realProcesses.any { it.packageName == pkgName }
                    if (alreadyAdded) continue

                    val appInfo = try {
                        packageManager.getApplicationInfo(pkgName, 0)
                    } catch (e: Exception) {
                        continue
                    }

                    val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))
                    var memory = 0L
                    if (memoryInfo.isNotEmpty()) {
                        memory = memoryInfo[0].totalPss * 1024L
                    }

                    val appName = try {
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        pkgName
                    }
                    val icon = try {
                        packageManager.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    val cpuUsage = try {
                        cpuMonitor.getCpuUsage(pid).processCpuUsage
                    } catch (e: Exception) {
                        0f
                    }

                    realProcesses.add(
                        ProcessInfo(
                            pid = pid,
                            uid = appInfo.uid,
                            processName = pkgName,
                            appName = appName,
                            packageName = pkgName,
                            icon = icon,
                            memoryUsage = memory,
                            cpuUsage = cpuUsage,
                            threadCount = 1,
                            isSystemApp = isSystemApp,
                            isRunning = true
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 按包名去重，保留内存最大的一个
        val uniqueProcesses = realProcesses.groupBy { it.packageName }
            .mapValues { (_, list) -&gt; list.maxByOrNull { it.memoryUsage }!! }
            .values
            .toList()

        return uniqueProcesses
    }

    fun formatMemory(bytes: Long): String {
        return when {
            bytes &gt;= 1024 * 1024 * 1024 -&gt; String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes &gt;= 1024 * 1024 -&gt; String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes &gt;= 1024 -&gt; String.format("%.2f KB", bytes / 1024.0)
            else -&gt; "$bytes B"
        }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT &gt;= Build.VERSION_CODES.LOLLIPOP) {
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
    
    override fun onCleared() {
        super.onCleared()
        isMonitoring = false
    }
}