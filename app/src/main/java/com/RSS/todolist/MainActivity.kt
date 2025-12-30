package com.RSS.todolist

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.RSS.todolist.service.ScreenCaptureService
import com.RSS.todolist.data.ChatRequest
import com.RSS.todolist.data.ChatMessage
import com.RSS.todolist.data.ChatResponse
import com.RSS.todolist.data.AiNetwork
import com.RSS.todolist.utils.AiConfigStore
import com.RSS.todolist.utils.TaskStore
import com.RSS.todolist.utils.TodoTask
import com.RSS.todolist.ui.theme.TodoListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 申请通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // 2. 启动服务保活 (发送 INIT 信号)
        // 这样一打开 App，通知栏就会显示助手已就绪，避免冷启动截屏失败
        //val initIntent = Intent(this, ScreenCaptureService::class.java).apply {
        //    action = ScreenCaptureService.ACTION_INIT
        //}
        //ContextCompat.startForegroundService(this, initIntent)

        setContent {
            TodoListTheme {
                // 3. 页面导航状态管理
                // false = 显示主页, true = 显示设置页
                var showSettings by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSettings) {
                        // 显示设置页，传入返回回调
                        SettingsScreen(onBack = { showSettings = false })
                    } else {
                        // 显示主页，传入打开设置的回调
                        MainScreen(onOpenSettings = { showSettings = true })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    // 数据状态：任务列表
    var tasks by remember { mutableStateOf(TaskStore.getTasks(context)) }

    // 弹窗状态：控制新增/编辑对话框
    var showDialog by remember { mutableStateOf(false) }
    var dialogText by remember { mutableStateOf("") }
    var editingIndex by remember { mutableIntStateOf(-1) } // -1表示新增，>=0表示编辑索引
    var useLlm by remember { mutableStateOf(false) }

    // 打开弹窗的逻辑
    fun openDialog(index: Int = -1, initialText: String = "") {
        editingIndex = index
        dialogText = initialText
        useLlm = false
        showDialog = true
    }

    // 保存任务逻辑
    fun saveTask() {
        if (dialogText.isBlank()) return
        
        if (editingIndex == -1) {
            // 新增：支持可选 LLM 提取
            if (useLlm) {
                Toast.makeText(context, "正在使用 LLM 提取信息...", Toast.LENGTH_SHORT).show()
                // 调用分析模型，异步回调添加任务
                val appConfig = AiConfigStore.getConfig(context)
                val anaConfig = appConfig.analysis
                val template = AiConfigStore.getAnalysisPrompt(context)
                val prompt = buildString {
                    append(template)
                    append("\n\n待处理文字：\n")
                    append(dialogText)
                }
                val message = ChatMessage(role = "user", content = prompt)
                val request = ChatRequest(model = anaConfig.modelName, messages = listOf(message))
                AiNetwork.createService(anaConfig).chat(request).enqueue(object : retrofit2.Callback<ChatResponse> {
                    override fun onResponse(call: retrofit2.Call<ChatResponse>, response: retrofit2.Response<ChatResponse>) {
                        var taskText = response.body()?.choices?.firstOrNull()?.message?.content
                        if (!taskText.isNullOrEmpty()) {
                            taskText = taskText.replace("输出：", "").replace("Output:", "").replace("Task:", "").replace("\"", "").trim()
                            if (taskText != "无任务") {
                                TaskStore.addTask(context, taskText)
                                // 仅通知 Service 增加新项（包含新索引），避免刷新全部通知
                                val tasks = TaskStore.getTasks(context)
                                val newIndex = tasks.size - 1
                                context.sendBroadcast(Intent(ScreenCaptureService.ACTION_REFRESH).apply {
                                    setPackage(context.packageName)
                                    putExtra(ScreenCaptureService.EXTRA_NEW_TASK_INDEX, newIndex)
                                })
                            } else {
                                // 若模型返回无任务，则回退为直接保存原始文本
                                TaskStore.addTask(context, dialogText)
                                val tasks = TaskStore.getTasks(context)
                                val newIndex = tasks.size - 1
                                context.sendBroadcast(Intent(ScreenCaptureService.ACTION_REFRESH).apply {
                                    setPackage(context.packageName)
                                    putExtra(ScreenCaptureService.EXTRA_NEW_TASK_INDEX, newIndex)
                                })
                            }
                        } else {
                            TaskStore.addTask(context, dialogText)
                            val tasks = TaskStore.getTasks(context)
                            val newIndex = tasks.size - 1
                            context.sendBroadcast(Intent(ScreenCaptureService.ACTION_REFRESH).apply {
                                setPackage(context.packageName)
                                putExtra(ScreenCaptureService.EXTRA_NEW_TASK_INDEX, newIndex)
                            })
                        }
                    }

                    override fun onFailure(call: retrofit2.Call<ChatResponse>, t: Throwable) {
                        // 网络失败则直接保存原始文本
                        TaskStore.addTask(context, dialogText)
                        val tasks = TaskStore.getTasks(context)
                        val newIndex = tasks.size - 1
                        context.sendBroadcast(Intent(ScreenCaptureService.ACTION_REFRESH).apply {
                            setPackage(context.packageName)
                            putExtra(ScreenCaptureService.EXTRA_NEW_TASK_INDEX, newIndex)
                        })
                    }
                })
            } else {
                TaskStore.addTask(context, dialogText)
                val tasks = TaskStore.getTasks(context)
                val newIndex = tasks.size - 1
                context.sendBroadcast(Intent(ScreenCaptureService.ACTION_REFRESH).apply {
                    setPackage(context.packageName)
                    putExtra(ScreenCaptureService.EXTRA_NEW_TASK_INDEX, newIndex)
                })
            }
        } else {
            // 编辑（增量更新）
            TaskStore.updateTask(context, editingIndex, dialogText)
            // 通知 Service 只更新该索引的通知，避免重建所有通知
            context.sendBroadcast(Intent(ScreenCaptureService.ACTION_REFRESH).apply {
                setPackage(context.packageName)
                putExtra(ScreenCaptureService.EXTRA_EDIT_TASK_INDEX, editingIndex)
            })
        }
        showDialog = false
    }

    // 监听数据变化，实时刷新 UI
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "tasks") {
                tasks = TaskStore.getTasks(context)
            }
        }
        val prefs = context.getSharedPreferences("todo_list_pref", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // 截屏回调处理
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Toast.makeText(context, "开始分析屏幕...", Toast.LENGTH_SHORT).show()
            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
            // 启动后让 App 退到后台，方便用户截取当前屏幕
            (context as? Activity)?.moveTaskToBack(true)
        }
    }

    // --- UI 结构 ---
    
    // 弹窗组件
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingIndex == -1) "新增任务" else "编辑任务") },
            text = {
                Column {
                    OutlinedTextField(
                        value = dialogText,
                        onValueChange = { dialogText = it },
                        label = { Text("任务内容") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useLlm, onCheckedChange = { useLlm = it })
                        Text("使用 LLM 提取信息", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { saveTask() }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的待办清单") },
                actions = {
                    // ⚙️ 设置按钮
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "配置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            
            // 主内容区域
            Column(modifier = Modifier.fillMaxSize()) {
                
                // 列表区域
                Box(modifier = Modifier.weight(1f)) {
                    if (tasks.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📝", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("暂无任务", color = Color.Gray)
                            Text("点击右下角 + 手动添加", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        }
                    } else {
                        LazyColumn(
                            // 底部留出空间给 FloatingActionButton 和截屏按钮
                            contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp, start = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(tasks) { index, task ->
                                TaskItemCard(
                                    task = task,
                                    onToggle = {
                                        // 勾选完成/取消完成
                                        val intent = Intent(ScreenCaptureService.ACTION_COMPLETE_TASK).apply {
                                            setPackage(context.packageName)
                                            putExtra(ScreenCaptureService.EXTRA_TASK_INDEX, index)
                                        }
                                        context.sendBroadcast(intent)
                                    },
                                    onEdit = { 
                                        // 点击编辑
                                        openDialog(index, task.text) 
                                    }
                                )
                            }
                        }
                    }
                }

                // 底部截屏按钮容器
                Surface(shadowElevation = 16.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent()) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📸 截屏识别新任务")
                        }
                    }
                }
            }

            // ➕ 悬浮添加按钮
            FloatingActionButton(
                onClick = { openDialog(-1, "") }, // -1 代表新增
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp, end = 24.dp), // 避开底部的截屏按钮
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    }
}

@Composable
fun TaskItemCard(task: TodoTask, onToggle: () -> Unit, onEdit: () -> Unit) {
    // 样式动态计算
    val textColor = if (task.isCompleted) Color.Gray else MaterialTheme.colorScheme.onSurface
    val textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
    val cardColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() }
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Text(
                text = task.text,
                color = textColor,
                textDecoration = textDecoration,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            // 编辑按钮
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}