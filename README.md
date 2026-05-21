# 进程管理器 (Process Manager)

一个功能强大的Android进程管理应用，使用现代Android开发技术栈构建。

## 功能特性

### 核心功能
- 📋 **进程查看** - 查看设备上所有运行的进程
- 📊 **自动分类** - 自动将进程分为系统进程、用户进程和服务
- 🔍 **进程搜索** - 快速搜索特定进程
- 📈 **内存监控** - 实时显示每个进程的内存使用情况
- 🛑 **终止进程** - 一键结束后台进程

### 附加功能
- 📱 **进程详情** - 查看进程的详细信息（PID、UID、包名等）
- 📊 **系统统计** - 显示内存使用情况和进程数量统计
- 🔄 **实时刷新** - 随时刷新进程列表
- 🎨 **Material Design** - 现代化的用户界面

## 技术栈

- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **架构**: MVVM
- **异步处理**: Kotlin Coroutines + Flow
- **最低SDK版本**: API 26 (Android 8.0)
- **目标SDK版本**: API 34 (Android 14)

## 项目结构

```
app/
├── src/main/
│   ├── java/com/processmanager/app/
│   │   ├── MainActivity.kt              # 主Activity
│   │   ├── models/
│   │   │   └── ProcessInfo.kt           # 进程数据模型
│   │   ├── viewmodels/
│   │   │   └── ProcessViewModel.kt      # 进程ViewModel
│   │   └── ui/screens/
│   │       ├── ProcessListScreen.kt     # 进程列表界面
│   │       ├── ProcessDetailScreen.kt   # 进程详情界面
│   │       └── StatsScreen.kt           # 统计界面
│   └── res/
│       └── ...
```

## 构建和运行

### 前置要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 8 或更高版本
- Android SDK API 34

### 构建步骤
1. 克隆项目
2. 使用Android Studio打开项目
3. 等待Gradle同步完成
4. 连接Android设备或启动模拟器
5. 点击运行按钮或使用命令：`./gradlew installDebug`

## 使用说明

1. **查看进程** - 打开应用即可看到所有运行的进程
2. **筛选分类** - 点击顶部标签筛选用户/系统/服务进程
3. **搜索进程** - 在搜索框输入关键词查找特定进程
4. **查看详情** - 点击任意进程查看详细信息
5. **结束进程** - 点击进程卡片右侧的X按钮结束进程
6. **查看统计** - 切换到底部"统计"标签查看系统信息

## 权限说明

应用需要以下权限：
- `KILL_BACKGROUND_PROCESSES` - 用于结束后台进程
- `GET_TASKS` - 用于获取运行中的任务信息

## 注意事项

- 由于Android系统的安全限制，某些系统进程无法被终止
- 终止系统进程可能导致设备不稳定，请谨慎操作
- 建议只终止您熟悉的第三方应用进程

## 许可证

本项目仅供学习和研究使用。
