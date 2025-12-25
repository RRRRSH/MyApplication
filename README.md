**项目概览**

- **用途**: 一个基于 Jetpack Compose 的 Android 待办事项应用（To‑Do），具有「截屏识别→OCR→智能提取任务→加入待办」的能力。

**主要功能**
- **手动管理任务**: 添加、编辑、标记完成、清空。
- **截屏识别新增任务**: 通过系统截屏权限捕获屏幕，上传到 OCR，再用推理模型提取一条标准任务并加入列表。
- **前台服务通知**: 在通知栏展示任务列表、支持一键完成与清空。

**项目结构（重要文件）**
- **入口/界面**: [app/src/main/java/com/RSS/todolist/MainActivity.kt](app/src/main/java/com/RSS/todolist/MainActivity.kt)
- **设置页**: [app/src/main/java/com/RSS/todolist/SettingsScreen.kt](app/src/main/java/com/RSS/todolist/SettingsScreen.kt)
- **截屏与后台服务**: [app/src/main/java/com/RSS/todolist/service/ScreenCaptureService.kt](app/src/main/java/com/RSS/todolist/service/ScreenCaptureService.kt)
- **任务存储**: [app/src/main/java/com/RSS/todolist/utils/TaskStore.kt](app/src/main/java/com/RSS/todolist/utils/TaskStore.kt)
- **AI 配置存取**: [app/src/main/java/com/RSS/todolist/utils/AiConfigStore.kt](app/src/main/java/com/RSS/todolist/utils/AiConfigStore.kt)
- **网络与模型定义**: [app/src/main/java/com/RSS/todolist/data](app/src/main/java/com/RSS/todolist/data)

**快速运行（开发机）**
1. 安装并打开 Android Studio。
2. 在 Android Studio 中打开本项目根目录（显示有 build.gradle.kts 的目录）。
3. 运行前：确保 Android SDK 已安装并配置到 local.properties（通常 Android Studio 会自动处理）。
4. 启动模拟器或连接真机，点击 Run 编译并安装。

**必须权限与交互**
- 应用会请求截屏授权（MediaProjection），首次截屏时系统会弹出授权对话。
- Android 13+ 会请求 POST_NOTIFICATIONS 权限以显示通知。

**配置 AI（OCR 与推理模型）**
- 推荐通过应用内「AI 模型配置」页面修改配置（Settings）。相关实现文件：
  - [app/src/main/java/com/RSS/todolist/utils/AiConfigStore.kt](app/src/main/java/com/RSS/todolist/utils/AiConfigStore.kt)
  - [app/src/main/java/com/RSS/todolist/SettingsScreen.kt](app/src/main/java/com/RSS/todolist/SettingsScreen.kt)
- 当前代码在开发期间可能包含可用的调试默认值；设置页顶部会在使用调试默认 Key 时显示醒目提示。发布前务必移除或替换为你自己的 Key。

**安全与发布注意事项**
- 严禁将真实 API Key 提交到公共仓库。已清理部分仓库内的硬编码 Key，但请再次检查整个代码库。
- 发布前：确保 AiConfigStore 中不含任何公开的敏感默认值，并在版本控制中忽略 local.properties 与任何包含密钥的文件。

**常见排错**
- 如果截屏无法获取图像，检查模拟器/真机的截屏权限与 Android 版本兼容性；相关逻辑在 ScreenCaptureService 内有重试和日志输出。
- 网络请求失败时，请在设置中确认 Base URL、API Key、Model Name 是否正确。

