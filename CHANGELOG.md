# Changelog

All notable changes to this project are documented in this file.

## 2025-12-26
- Added Quick Settings Tile `QuickCaptureTile` (app/src/main/java/com/RSS/todolist/QuickCaptureTile.kt) — allows triggering screen capture from system quick settings. Uses `PendingIntent` to start the authorization Activity for compatibility.
- Added transparent authorization Activity `CaptureStarterActivity` (app/src/main/java/com/RSS/todolist/CaptureStarterActivity.kt) to request MediaProjection permission and forward the result to `ScreenCaptureService`.
- Added editable prompts in Settings: OCR Prompt and Analysis Prompt (AiConfigStore.kt / SettingsScreen.kt). Prompts can be edited, saved, and reset to built-in defaults.
- `ScreenCaptureService` now reads OCR/Analysis prompts from `AiConfigStore` at runtime.
- Notification and icons updated: use `res/drawable/gemini_generated_image.png` as adaptive foreground; added `res/drawable/ic_notification.xml` for monochrome notification small icon.
- Removed/cleaned hardcoded API keys in the repo; README warns to remove any keys before publishing.
- Fixed Kotlin constant initialization issue: long prompt constants converted from `const val` to `private val` to allow `trimIndent()`.
- Fixed Quick Tile launch method: replaced `startActivityAndCollapse` with `PendingIntent.send()` and added logging/exception handling.

(If you want per-apply-patch level history, I can append each tool/apply_patch summary as separate lines.)