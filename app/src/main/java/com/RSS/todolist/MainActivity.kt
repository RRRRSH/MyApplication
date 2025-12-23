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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.RSS.todolist.utils.TodoTask // 导入新模型
import com.RSS.todolist.ui.theme.TodoListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
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

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (tasks.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉", fontSize = 48.sp)
                    Text("无记录", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(tasks) { index, task ->
                        TaskItemCard(
                            task = task,
                            onToggle = {
                                // 发送广播切换状态
                                val intent = Intent(ScreenCaptureService.ACTION_COMPLETE_TASK).apply {
                                    setPackage(context.packageName)
                                    putExtra(ScreenCaptureService.EXTRA_TASK_INDEX, index)
                                }
                                context.sendBroadcast(intent)
                                
                                // 为了让 UI 响应更快，也可以直接调用 Store (可选)
                                // TaskStore.toggleTaskCompletion(context, index)
                            }
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
}

@Composable
fun TaskItemCard(task: TodoTask, onToggle: () -> Unit) {
    // 根据完成状态决定颜色和样式
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = task.text,
                color = textColor,
                textDecoration = textDecoration,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}