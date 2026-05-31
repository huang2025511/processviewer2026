package com.processmanager.app.utils

class CpuMonitor {
    private var lastCpuTime: Long = 0
    private var lastProcessCpuTime: Long = 0
    
    data class CpuUsage(
        val systemCpuUsage: Float,
        val processCpuUsage: Float
    )
    
    fun getCpuUsage(processId: Int): CpuUsage {
        val currentCpuTime = readSystemCpuTime()
        val currentProcessCpuTime = readProcessCpuTime(processId)
        
        val cpuDelta = currentCpuTime - lastCpuTime
        val processCpuDelta = currentProcessCpuTime - lastProcessCpuTime
        
        lastCpuTime = currentCpuTime
        lastProcessCpuTime = currentProcessCpuTime
        
        val systemUsage = if (cpuDelta > 0) {
            (processCpuDelta.toFloat() / cpuDelta.toFloat()) * 100f
        } else {
            0f
        }
        
        return CpuUsage(
            systemCpuUsage = systemUsage.coerceIn(0f, 100f),
            processCpuUsage = systemUsage.coerceIn(0f, 100f)
        )
    }
    
    private fun readSystemCpuTime(): Long {
        return try {
            val lines = java.io.File("/proc/stat").readLines()
            for (line in lines) {
                if (line.startsWith("cpu ")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 5) {
                        val user = parts[1].toLongOrNull() ?: 0
                        val nice = parts[2].toLongOrNull() ?: 0
                        val system = parts[3].toLongOrNull() ?: 0
                        val idle = parts[4].toLongOrNull() ?: 0
                        return user + nice + system + idle
                    }
                }
            }
            0L
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun readProcessCpuTime(processId: Int): Long {
        return try {
            val statFile = java.io.File("/proc/$processId/stat")
            if (statFile.exists()) {
                val content = statFile.readText()
                val parts = content.split(" ")
                if (parts.size >= 17) {
                    val utime = parts[13].toLongOrNull() ?: 0
                    val stime = parts[14].toLongOrNull() ?: 0
                    return utime + stime
                }
            }
            0L
        } catch (e: Exception) {
            0L
        }
    }
}
