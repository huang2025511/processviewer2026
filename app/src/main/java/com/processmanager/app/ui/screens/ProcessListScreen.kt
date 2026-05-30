package com.processmanager.app.ui.screens

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.processmanager.app.models.ProcessCategory
import com.processmanager.app.models.ProcessInfo
import com.processmanager.app.models.SortBy
import com.processmanager.app.viewmodels.ProcessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessListScreen(
    viewModel: ProcessViewModel,
    onProcessClick: (ProcessInfo) -> Unit
) {
    val context = LocalContext.current
    val processes by viewModel.processes.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasPermission = remember { mutableStateOf(viewModel.hasUsageStatsPermission(context)) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    // 先获取所有过滤后的进程，然后按排序方式排序
    val filteredProcesses = remember(processes, selectedCategory, searchQuery, sortBy) {
        val allFiltered = viewModel.getFilteredProcesses()
        when (sortBy) {
            SortBy.MEMORY -> {
                allFiltered.sortedWith(
                    compareByDescending<ProcessInfo> { it.isRunning }
                        .thenByDescending { it.memoryUsage }
                        .thenBy { it.appName }
                )
            }
            SortBy.CPU -> {
                allFiltered.sortedWith(
                    compareByDescending<ProcessInfo> { it.isRunning }
                        .thenByDescending { it.cpuUsage }
                        .thenByDescending { it.memoryUsage }
                        .thenBy { it.appName }
                )
            }
            SortBy.NAME -> {
                allFiltered.sortedWith(
                    compareByDescending<ProcessInfo> { it.isRunning }
                        .thenBy { it.appName }
                        .thenByDescending { it.memoryUsage }
                )
            }
            SortBy.RUNNING -> {
                allFiltered.sortedWith(
                    compareByDescending<ProcessInfo> { it.isRunning }
                        .thenByDescending { it.memoryUsage }
                        .thenBy { it.appName }
                )
            }
        }
    }

    // 确保权限变化时更新
    LaunchedEffect(Unit) {
        hasPermission.value = viewModel.hasUsageStatsPermission(context)
    }
    
    LaunchedEffect(Unit) {
        if (!hasPermission.value) {
            viewModel.openUsageStatsSettings(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("进程管理器") },
                actions = {
                    // 排序按钮
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("按内存排序", fontWeight = if (sortBy == SortBy.MEMORY) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    viewModel.setSortBy(SortBy.MEMORY)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortBy == SortBy.MEMORY) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("按CPU排序", fontWeight = if (sortBy == SortBy.CPU) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    viewModel.setSortBy(SortBy.CPU)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortBy == SortBy.CPU) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("按名称排序", fontWeight = if (sortBy == SortBy.NAME) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    viewModel.setSortBy(SortBy.NAME)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortBy == SortBy.NAME) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("按运行状态排序", fontWeight = if (sortBy == SortBy.RUNNING) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    viewModel.setSortBy(SortBy.RUNNING)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortBy == SortBy.RUNNING) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                    
                    IconButton(onClick = {
                        hasPermission.value = viewModel.hasUsageStatsPermission(context)
                        if (hasPermission.value) {
                            viewModel.loadProcesses(context)
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasPermission.value) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "权限警告",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "需要权限",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "请授予\"使用情况访问\"权限以查看完整进程列表",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.openUsageStatsSettings(context) }) {
                            Text("前往设置")
                        }
                    }
                }
            }

            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            CategoryTabs(
                selectedCategory = selectedCategory,
                onCategorySelected = { viewModel.setCategory(it) }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                if (filteredProcesses.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "无结果",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "未找到进程",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = filteredProcesses,
                            key = { process -> "${process.pid}-${process.packageName}" }
                        ) { process ->
                            ProcessItem(
                                process = process,
                                viewModel = viewModel,
                                onClick = { onProcessClick(process) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("搜索进程...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun CategoryTabs(
    selectedCategory: ProcessCategory,
    onCategorySelected: (ProcessCategory) -> Unit
) {
    val categories = listOf(
        ProcessCategory.ALL to "全部",
        ProcessCategory.USER to "用户",
        ProcessCategory.SYSTEM to "系统",
        ProcessCategory.SERVICE to "服务"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { (category, label) ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(label) },
                leadingIcon = if (selectedCategory == category) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
        }
    }
}

@Composable
fun ProcessItem(
    process: ProcessInfo,
    viewModel: ProcessViewModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (process.isRunning) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 使用更安全的图标显示方式
            val iconBitmap = remember(process.packageName) {
                try {
                    process.icon?.let {
                        when (it) {
                            is BitmapDrawable -> it.bitmap.asImageBitmap()
                            else -> null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            }
            
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = process.appName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = process.appName,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                    text = process.appName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (process.isRunning) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                    if (process.isRunning) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Text(
                    text = process.processName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "内存: ${viewModel.formatMemory(process.memoryUsage)}",
                    fontSize = 12.sp,
                    color = if (process.memoryUsage > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // 所有进程都显示结束按钮，运行中彩色，非运行灰色
            IconButton(
                onClick = { viewModel.killProcess(context, process) }
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "结束进程",
                    tint = if (process.isRunning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}
