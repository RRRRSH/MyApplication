# 项目概览

- 用途: 一个基于 Jetpack Compose 的 Android 待办事项应用（ToDo），具有截屏识别  OCR  智能提取任务  加入待办的能力。

## 主要功能
# 项目概览

用途
----

一个基于 Jetpack Compose 的 Android 待办事项应用（To‑Do），具有“截屏识别 → OCR → 智能提取任务 → 加入待办”的能力。

主要功能
----

- 手动管理任务：添加、编辑、标记完成、清空。
- 截屏识别新增任务：通过系统截屏权限捕获屏幕，上传到 OCR，再用推理模型提取一条标准任务并加入列表。
- 前台服务通知：在通知栏展示任务列表、支持一键完成与清空。

项目结构（重要文件）
----

- 入口/界面: [app/src/main/java/com/RSS/todolist/MainActivity.kt](app/src/main/java/com/RSS/todolist/MainActivity.kt)
- 设置页: [app/src/main/java/com/RSS/todolist/SettingsScreen.kt](app/src/main/java/com/RSS/todolist/SettingsScreen.kt)
- 截屏与后台服务: [app/src/main/java/com/RSS/todolist/service/ScreenCaptureService.kt](app/src/main/java/com/RSS/todolist/service/ScreenCaptureService.kt)
- 任务存储: [app/src/main/java/com/RSS/todolist/utils/TaskStore.kt](app/src/main/java/com/RSS/todolist/utils/TaskStore.kt)
- AI 配置存取: [app/src/main/java/com/RSS/todolist/utils/AiConfigStore.kt](app/src/main/java/com/RSS/todolist/utils/AiConfigStore.kt)
- 网络与模型定义: [app/src/main/java/com/RSS/todolist/data](app/src/main/java/com/RSS/todolist/data)

快速运行（开发机）
----

1. 安装并打开 Android Studio。
2. 在 Android Studio 中打开本项目根目录（包含 `build.gradle.kts`）。
3. 运行前：确保 Android SDK 已安装并配置到 `local.properties`（通常 Android Studio 会自动处理）。
4. 启动模拟器或连接真机，点击 Run 编译并安装。

必须权限与交互
----

- 应用会请求截屏授权（MediaProjection），首次截屏时系统会弹出授权对话。
- Android 13+ 会请求 `POST_NOTIFICATIONS` 权限以显示通知。

配置 AI（OCR 与推理模型）
----

- 推荐通过应用内“AI 模型配置”页面修改配置（Settings）。相关实现文件：
  - `app/src/main/java/com/RSS/todolist/utils/AiConfigStore.kt`
  - `app/src/main/java/com/RSS/todolist/SettingsScreen.kt`
- 当前代码在开发期间可能包含可用的调试默认值；设置页顶部会在使用调试默认 Key 时显示醒目提示。发布前务必移除或替换为你自己的 Key。

安全与发布注意事项
----

- 严禁将真实 API Key 提交到公共仓库。已清理部分仓库内的硬编码 Key，但请再次检查整个代码库。
- 发布前：确保 `AiConfigStore` 中不含任何公开的敏感默认值，并在版本控制中忽略 `local.properties` 与任何包含密钥的文件。

常见排错
----

- 如果截屏无法获取图像，检查模拟器/真机的截屏权限与 Android 版本兼容性；相关逻辑在 `ScreenCaptureService` 内有重试和日志输出。
- 网络请求失败时，请在设置中确认 Base URL、API Key、Model Name 是否正确。

新增 / 最近更改
----

查看运行时日志（示例）：

```bash
adb logcat -s QuickCaptureTile CaptureStarterActivity ScreenCaptureService
```

快速设置磁贴通常需要用户手动添加：下拉快速设置 → 点击编辑（铅笔）→ 将 “截屏识别” 磁贴拖入可见面板。

变更日志 (Changelog)
----

### 2025-12-26
- 添加 Quick Settings 磁贴 `QuickCaptureTile`（`app/src/main/java/com/RSS/todolist/QuickCaptureTile.kt`），支持快速触发截屏授权（使用 `PendingIntent` 启动以兼容受限环境）。
- 新增透明授权 Activity `CaptureStarterActivity`（`app/src/main/java/com/RSS/todolist/CaptureStarterActivity.kt`），用于请求 MediaProjection 授权并将结果转发给 `ScreenCaptureService`。
- 在设置页添加 OCR Prompt 与 Analysis Prompt 的编辑/保存/重置功能（`AiConfigStore.kt` / `SettingsScreen.kt`），并让 `ScreenCaptureService` 在运行时读取这些 Prompt。
- 通知与图标更新：使用 `res/drawable/gemini_generated_image.png` 作为 adaptive 前景图，并新增 `res/drawable/ic_notification.xml` 作为单色通知小图标。
- 移除/清理仓库中的硬编码 API Key 并在 README 中增加发布前的安全提醒。
- 修复 Kotlin 常量初始化问题：将内置长文本 prompt 从 `const val` 改为 `private val`（允许 `trimIndent()`），解决编译错误。
- 修复 Quick Tile 启动逻辑（从 `startActivityAndCollapse` 改为 `PendingIntent.send()`），并加入日志/异常处理以便诊断设备差异。

### 2025-12-27
- 在设置页添加并持久化“推理模型使用相同配置”开关，支持记住上次勾选状态（`AiConfigStore.kt` / `SettingsScreen.kt`）。
- 支持可编辑的“默认 Prompt”：用户可将当前 Prompt 保存为默认或恢复内置默认，清除应用数据后回退到内置值（`AiConfigStore.kt` / `SettingsScreen.kt`）。
- 优化设置页按钮布局：将 Prompt 区域的操作按钮分为两行（主要操作 + 次要操作）以改善排列与可用性（`SettingsScreen.kt`）。
- 将通知内容格式化为与分析输出匹配的样式（如示例中的 Markdown 结构），并在通知中把“关键信息”放大加粗以突出显示（`ScreenCaptureService.kt`）。
- 增强地点解析：在分析 prompt 与通知解析逻辑中优先合并品牌与地点（例如把“顺丰”与“北门驿站”合并为“顺丰北门驿站”），从而让通知标题直接显示更准确的地点信息（`AiConfigStore.kt` / `ScreenCaptureService.kt`）。
### 2025-12-30
- 在手动新增/编辑任务对话中加入“使用 LLM 提取信息”复选框（默认不选），位于 `MainActivity.kt` 的新增任务弹窗中。
  - 行为：勾选后点击保存会异步调用分析模型（使用 `AiConfigStore` 中的分析配置与 Prompt），模型返回的提取结果将作为任务文本加入 `TaskStore`；若模型返回“无任务”或请求失败，则回退保存原始输入文本。
  - 实现细节：异步调用由 `AiNetwork` 发起，回调中写入任务并通过广播 `ScreenCaptureService.ACTION_REFRESH` 更新通知栏；对话在提交后保持当前行为（立即关闭），LLM 在后台运行并在回调时更新数据。
  - 相关文件：`app/src/main/java/com/RSS/todolist/MainActivity.kt`、`app/src/main/java/com/RSS/todolist/data/AiNetwork.kt`、`app/src/main/java/com/RSS/todolist/utils/AiConfigStore.kt`。

- 修复“点击快速设置磁贴后有概率无法进入截屏，需先打开 App 才正常”的问题：
  - 使用 `unlockAndRun {}` 覆盖锁屏/半锁屏场景。
  - 统一优先走 `startActivityAndCollapse(PendingIntent)`（比直接 `Intent` 更符合系统对用户触发启动的判定）。
  - Android 14+ 兜底：`PendingIntent.send(..., ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`，降低后台启动 Activity 被拦的概率。
  - 相关文件：`app/src/main/java/com/RSS/todolist/QuickCaptureTile.kt`。

- 进一步的通知与刷新改进：
  - 新增增量通知更新逻辑：新增任务时会发送带 `EXTRA_NEW_TASK_INDEX` 的广播，服务端（`ScreenCaptureService.kt`）只为该索引发布新通知而不清空全部通知，从而避免通知栏闪烁。
  - 编辑任务也改为增量更新：编辑时会发送 `EXTRA_EDIT_TASK_INDEX`，`ScreenCaptureService` 优先处理该索引（重新发布或取消对应通知），并仅更新主通知摘要（任务计数/文本）。
  - 为支持增量更新，新增 `addSingleTaskNotification(index)` 帮助方法，并在广播接收逻辑中优先处理 `EXTRA_EDIT_TASK_INDEX`，其次处理 `EXTRA_NEW_TASK_INDEX`，最后才回退到全量刷新（`showTaskNotification()`）。
  - 修复了 `ScreenCaptureService.kt` 中重复声明 `EXTRA_NEW_TASK_INDEX` 常量导致的编译冲突（重复声明已移除）。
  - 注意：当前通知 id 仍基于列表索引（`NOTIFICATION_ID_START + index`），索引重排仍可能带来边界情况；推荐后续将任务改为持久 `UUID` 并基于该 `id` 生成稳定的通知 id（README 上下文中已有迁移建议）。


**Apply_patch 摘要（供审计）**

1. 添加 `getUseSameConfig` / `saveUseSameConfig` 到 `app/src/main/java/com/RSS/todolist/utils/AiConfigStore.kt`，用于持久化“推理模型使用相同配置”开关。
2. 更新 `app/src/main/java/com/RSS/todolist/SettingsScreen.kt`：
  - 使用 `AiConfigStore.getUseSameConfig(context)` 初始化 `useSameConfig`，在勾选时同步分析模型字段；保存时写回该布尔值。
  - 在 Prompt 区域添加 “保存为默认” 与 “恢复内置默认” 操作，并将按钮分两行排列以改善布局。
3. 在 `AiConfigStore.kt` 中新增可编辑的默认 Prompt API：`getSavedDefaultAnalysisPrompt` / `saveDefaultAnalysisPrompt` / `clearSavedDefaultAnalysisPrompt`，以及对应 OCR 的 `getSavedDefaultOcrPrompt` / `saveDefaultOcrPrompt` / `clearSavedDefaultOcrPrompt`，并保留内置常量 `DEFAULT_*` 作为最终回退。
4. 修改 `app/src/main/java/com/RSS/todolist/service/ScreenCaptureService.kt`：
  - 将 AI 分析结果按行解析，生成结构化通知内容（时间/地点/关键信息），并使用 `SpannableStringBuilder` 把关键信息加粗并放大显示。
  - 将通知的折叠视图标题设为地点（若无则为解析到的第一行），折叠内容为仅关键信息；展开视图顶部显示纯标题行，随后显示带标签的字段。
5. 更新默认分析 Prompt（`AiConfigStore.kt` 中的 `DEFAULT_ANALYSIS_PROMPT`），在 prompt 中明确要求合并品牌与地点（例如输出 `顺丰北门驿站`），并提供示例以引导模型输出符合新格式。
6. 在 `ScreenCaptureService.kt` 中增加品牌识别逻辑（顺丰/丰巢/菜鸟/京东/EMS/申通/中通/圆通/安能 等），在解析结果中尽量将品牌与地点合并为 `locationStr`，以便通知标题直接显示品牌+地点。


