package com.RSS.todolist

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.RSS.todolist.utils.AiConfigStore
import com.RSS.todolist.utils.AiModelConfig
import com.RSS.todolist.utils.AppAiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentConfig = remember { AiConfigStore.getConfig(context) }

    // OCR 状态
    var ocrBaseUrl by remember { mutableStateOf(currentConfig.ocr.baseUrl) }
    var ocrApiKey by remember { mutableStateOf(currentConfig.ocr.apiKey) }
    var ocrModel by remember { mutableStateOf(currentConfig.ocr.modelName) }
    var ocrAppId by remember { mutableStateOf(currentConfig.ocr.appId ?: "") }

    // 分析 状态
    var anaBaseUrl by remember { mutableStateOf(currentConfig.analysis.baseUrl) }
    var anaApiKey by remember { mutableStateOf(currentConfig.analysis.apiKey) }
    var anaModel by remember { mutableStateOf(currentConfig.analysis.modelName) }
    var anaAppId by remember { mutableStateOf(currentConfig.analysis.appId ?: "") }

    // 🌟 新增：控制是否同步的开关
    // 如果两个配置的 URL 和 Key 相同，默认视为开启同步
    var useSameConfig by remember {
        mutableStateOf(currentConfig.ocr.apiKey == currentConfig.analysis.apiKey && currentConfig.ocr.baseUrl == currentConfig.analysis.baseUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 模型配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. 核心模型配置 (OCR/通用)
            ConfigSection(
                title = if (useSameConfig) "🤖 通用模型配置" else "👁️ 视觉模型 (OCR)",
                desc = if (useSameConfig) "既负责看图，也负责分析任务 (需支持视觉)" else "专门负责看图识字",
                baseUrl = ocrBaseUrl, onUrlChange = { ocrBaseUrl = it },
                apiKey = ocrApiKey, onKeyChange = { ocrApiKey = it },
                model = ocrModel, onModelChange = { ocrModel = it },
                appId = ocrAppId, onAppIdChange = { ocrAppId = it }
            )

            HorizontalDivider()

            // 🌟 开关区域
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Checkbox(
                    checked = useSameConfig,
                    onCheckedChange = { useSameConfig = it }
                )
                Text(
                    text = "推理模型使用相同配置",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // 2. 分析模型配置 (仅当不同步时显示)
            if (!useSameConfig) {
                ConfigSection(
                    title = "🧠 推理模型 (分析)",
                    desc = "负责提取任务，可用更便宜的纯文本模型",
                    baseUrl = anaBaseUrl, onUrlChange = { anaBaseUrl = it },
                    apiKey = anaApiKey, onKeyChange = { anaApiKey = it },
                    model = anaModel, onModelChange = { anaModel = it },
                    appId = anaAppId, onAppIdChange = { anaAppId = it }
                )
                
                HorizontalDivider()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 保存按钮
            Button(
                onClick = {
                    // 准备 OCR 配置
                    val newOcr = AiModelConfig(ocrBaseUrl.trim(), ocrApiKey.trim(), ocrModel.trim(), ocrAppId.trim().ifEmpty { null })
                    
                    // 准备 分析 配置
                    val newAna = if (useSameConfig) {
                        // 🌟 如果勾选了同步，直接复制 OCR 的配置
                        newOcr.copy()
                    } else {
                        // 否则使用单独填写的配置
                        AiModelConfig(anaBaseUrl.trim(), anaApiKey.trim(), anaModel.trim(), anaAppId.trim().ifEmpty { null })
                    }
                    
                    AiConfigStore.saveConfig(context, AppAiConfig(newOcr, newAna))
                    Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("保存配置")
            }
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
fun ConfigSection(
    title: String, desc: String,
    baseUrl: String, onUrlChange: (String) -> Unit,
    apiKey: String, onKeyChange: (String) -> Unit,
    model: String, onModelChange: (String) -> Unit,
    appId: String, onAppIdChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        OutlinedTextField(
            value = baseUrl, onValueChange = onUrlChange,
            label = { Text("Base URL") },
            placeholder = { Text("https://...") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = apiKey, onValueChange = onKeyChange,
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = model, onValueChange = onModelChange,
            label = { Text("Model Name") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = appId, onValueChange = onAppIdChange,
            label = { Text("App ID (选填)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
    }
}