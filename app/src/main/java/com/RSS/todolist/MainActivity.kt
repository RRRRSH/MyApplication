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
import com.RSS.todolist.utils.TaskStore
import com.RSS.todolist.utils.TodoTask
import com.RSS.todolist.ui.theme.TodoListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 申请通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // 🌟 2. 核心修改：App 一启动，立刻强制启动前台服务 (Action = INIT)
        val initIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_INIT
        }
        ContextCompat.startForegroundService(this, initIntent)

        setContent {
            TodoListTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = { Text("我的待办清单") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    var tasks by remember { mutableStateOf(TaskStore.getTasks(context)) }

    var showDialog by remember { mutableStateOf(false) }
    var dialogText by remember { mutableStateOf("") }
    var editingIndex by remember { mutableIntStateOf(-1) }

    fun openDialog(index: Int = -1, initialText: String = "") {
        editingIndex = index
        dialogText = initialText
        showDialog = true
    }

    fun saveTask() {
        if (dialogText.isBlank()) return
        if (editingIndex == -1) {
            TaskStore.addTask(context, dialogText)
        } else {
            TaskStore.updateTask(context, editingIndex, dialogText)
        }
        context.sendBroadcast(Intent(ScreenCaptureService.ACTION_REFRESH).apply {
            setPackage(context.packageName)
        })
        showDialog = false
    }

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
            (context as? Activity)?.moveTaskToBack(true)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingIndex == -1) "新增任务" else "编辑任务") },
            text = {
                OutlinedTextField(
                    value = dialogText,
                    onValueChange = { dialogText = it },
                    label = { Text("任务内容") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton(onClick = { saveTask() }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                if (tasks.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📝", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("暂无任务", color = Color.Gray)
                        Text("点击 + 号添加", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(tasks) { index, task ->
                            TaskItemCard(
                                task = task,
                                onToggle = {
                                    val intent = Intent(ScreenCaptureService.ACTION_COMPLETE_TASK).apply {
                                        setPackage(context.packageName)
                                        putExtra(ScreenCaptureService.EXTRA_TASK_INDEX, index)
                                    }
                                    context.sendBroadcast(intent)
                                },
                                onEdit = { openDialog(index, task.text) }
                            )
                        }
                    }
                }
            }

            Surface(shadowElevation = 8.dp) {
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

        FloatingActionButton(
            onClick = { openDialog(-1, "") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add")
        }
    }
}

@Composable
fun TaskItemCard(task: TodoTask, onToggle: () -> Unit, onEdit: () -> Unit) {
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
            if (!task.isCompleted) {
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
}