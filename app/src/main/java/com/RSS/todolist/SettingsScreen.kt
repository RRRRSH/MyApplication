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
    val usingDebugDefaults = remember { AiConfigStore.isUsingDebugDefaults(context) }

    // OCR 状态
    var ocrBaseUrl by remember { mutableStateOf(currentConfig.ocr.baseUrl) }
    var ocrApiKey by remember { mutableStateOf(currentConfig.ocr.apiKey) }
    var ocrModel by remember { mutableStateOf(currentConfig.ocr.modelName) }
    var ocrAppId by remember { mutableStateOf(currentConfig.ocr.appId ?: "") }
    // OCR prompt 可编辑
    var ocrPrompt by remember { mutableStateOf(AiConfigStore.getOcrPrompt(context)) }
    var defaultOcrPrompt by remember { mutableStateOf(AiConfigStore.getSavedDefaultOcrPrompt(context)) }

    // 分析 状态
    var anaBaseUrl by remember { mutableStateOf(currentConfig.analysis.baseUrl) }
    var anaApiKey by remember { mutableStateOf(currentConfig.analysis.apiKey) }
    var anaModel by remember { mutableStateOf(currentConfig.analysis.modelName) }
    var anaAppId by remember { mutableStateOf(currentConfig.analysis.appId ?: "") }
    // 分析 模型 prompt
    var anaPrompt by remember { mutableStateOf(AiConfigStore.getAnalysisPrompt(context)) }
    var defaultAnaPrompt by remember { mutableStateOf(AiConfigStore.getSavedDefaultAnalysisPrompt(context)) } // 可编辑的默认值（持久化）

    // 🌟 新增：控制是否同步的开关（持久化）
    var useSameConfig by remember {
        mutableStateOf(AiConfigStore.getUseSameConfig(context))
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
            // 如果正在使用内置调试 Key，显示醒目提示
            if (usingDebugDefaults) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("⚠️ 正在使用内置调试 API Key", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("当前为调试默认配置，发布前请务必移除或替换为你自己的 Key", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            // 1. 核心模型配置 (OCR/通用)
            ConfigSection(
                title = if (useSameConfig) "🤖 通用模型配置" else "👁️ 视觉模型 (OCR)",
                desc = if (useSameConfig) "既负责看图，也负责分析任务 (需支持视觉)" else "专门负责看图识字",
                baseUrl = ocrBaseUrl, onUrlChange = { ocrBaseUrl = it },
                apiKey = ocrApiKey, onKeyChange = { ocrApiKey = it },
                model = ocrModel, onModelChange = { ocrModel = it },
                appId = ocrAppId, onAppIdChange = { ocrAppId = it }
            )

            // OCR Prompt 编辑（用于提取文本的提示词）
            Text("OCR 提示词", fontWeight = FontWeight.Bold)
            Text("用于控制 OCR 返回的文本格式，通常为“只返回识别到的文字”。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            OutlinedTextField(
                value = ocrPrompt,
                onValueChange = { ocrPrompt = it },
                label = { Text("OCR Prompt") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 4
            )
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        AiConfigStore.saveOcrPrompt(context, ocrPrompt)
                        Toast.makeText(context, "OCR Prompt 已保存", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("保存 OCR Prompt")
                    }
                    TextButton(onClick = {
                        // 重置当前编辑的 OCR prompt 为“当前默认”（可能是用户保存的默认）
                        ocrPrompt = defaultOcrPrompt
                        AiConfigStore.saveOcrPrompt(context, defaultOcrPrompt)
                        Toast.makeText(context, "已重置为默认 OCR Prompt", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("重置为默认")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = {
                        AiConfigStore.saveDefaultOcrPrompt(context, ocrPrompt)
                        defaultOcrPrompt = AiConfigStore.getSavedDefaultOcrPrompt(context)
                        Toast.makeText(context, "已将当前 OCR Prompt 保存为默认", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("保存为默认")
                    }
                    TextButton(onClick = {
                        AiConfigStore.clearSavedDefaultOcrPrompt(context)
                        defaultOcrPrompt = AiConfigStore.getSavedDefaultOcrPrompt(context)
                        Toast.makeText(context, "已恢复内置 OCR 默认 Prompt", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("恢复内置默认")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("当前默认 OCR 提示词（供参考）：", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(text = defaultOcrPrompt, modifier = Modifier.padding(8.dp))
            }

            HorizontalDivider()

            // 🌟 开关区域
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Checkbox(
                    checked = useSameConfig,
                    onCheckedChange = { checked ->
                        useSameConfig = checked
                        if (checked) {
                            // 勾选时同步当前 OCR 填写的字段到分析模型字段，便于保存
                            anaBaseUrl = ocrBaseUrl
                            anaApiKey = ocrApiKey
                            anaModel = ocrModel
                            anaAppId = ocrAppId
                        }
                    }
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

            // Prompt 编辑区域（用于分析模型）
            Text("推理模型提示词 (Prompt)", fontWeight = FontWeight.Bold)
            Text("编辑用于将 OCR 文本转换为标准待办的提示词。可点击重置为默认值。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            OutlinedTextField(
                value = anaPrompt,
                onValueChange = { anaPrompt = it },
                label = { Text("Analysis Prompt") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                singleLine = false,
                maxLines = 10
            )
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        // 保存时会另存 prompt
                        AiConfigStore.saveAnalysisPrompt(context, anaPrompt)
                        Toast.makeText(context, "Prompt 已保存", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("保存 Prompt")
                    }
                    TextButton(onClick = {
                        anaPrompt = defaultAnaPrompt
                        AiConfigStore.saveAnalysisPrompt(context, defaultAnaPrompt)
                        Toast.makeText(context, "已重置为默认 Prompt", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("重置为默认")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = {
                        AiConfigStore.saveDefaultAnalysisPrompt(context, anaPrompt)
                        defaultAnaPrompt = AiConfigStore.getSavedDefaultAnalysisPrompt(context)
                        Toast.makeText(context, "已将当前 Prompt 保存为默认", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("保存为默认")
                    }
                    TextButton(onClick = {
                        AiConfigStore.clearSavedDefaultAnalysisPrompt(context)
                        defaultAnaPrompt = AiConfigStore.getSavedDefaultAnalysisPrompt(context)
                        Toast.makeText(context, "已恢复内置默认 Prompt", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("恢复内置默认")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("当前默认提示词（供参考）：", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(text = defaultAnaPrompt, modifier = Modifier.padding(8.dp))
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
                    // 保存是否使用相同配置的状态
                    AiConfigStore.saveUseSameConfig(context, useSameConfig)
                    // 同步保存 prompt
                    AiConfigStore.saveAnalysisPrompt(context, anaPrompt)
                    AiConfigStore.saveOcrPrompt(context, ocrPrompt)
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