package com.RSS.todolist.utils

/**
 * 将 LLM 的分析输出解析为多个待办任务块。
 *
 * 约定：优先识别以 "## " 开头的 Markdown 小节作为一个任务。
 * 若无标题小节，则按空行分段作为兜底。
 * 返回的每个元素都应当可直接作为 TaskStore 的 task.text。
 */
object TaskExtraction {

    private fun sanitizeOcrTextForAnalysis(raw: String): String {
        var text = raw.replace("\r\n", "\n").trim()
        if (text.isBlank()) return ""

        // 去掉整段外层引号（OCR/多模态模型有时会把内容包进 "..."）
        text = text.removeSurrounding("\"", "\"").trim()

        // 逐行过滤常见的“描述性包装”内容
        val dropLinePatterns = listOf(
            Regex("^here'?s\\s+a\\s+text\\s+message.*", RegexOption.IGNORE_CASE),
            Regex("^the\\s+time\\s+is\\s+.*", RegexOption.IGNORE_CASE),
            Regex("^this\\s+is\\s+a\\s+text\\s+message.*", RegexOption.IGNORE_CASE)
        )

        val cleanedLines = text.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty()) return@mapNotNull ""
            if (dropLinePatterns.any { it.matches(t) }) return@mapNotNull null
            // 去掉单行前后的引号
            t.trim('"')
        }

        return cleanedLines.joinToString("\n").trim()
    }

    fun formatMultiMessageInput(raw: String): String {
        val text = sanitizeOcrTextForAnalysis(raw)
        if (text.isBlank()) return ""

        // 经验规则：短信聚类分隔符
        // - 空行
        // - 单独一行时间戳（如 3:21 PM / 15:21 / 3:21PM）
        // - 包含 "SMS" 的行
        val timeStampLine = Regex("^\\s*(\\d{1,2}:\\d{2})(\\s*[AP]M)?\\s*$", RegexOption.IGNORE_CASE)
        // 经验规则：某些 OCR 会把多条短信连成一段，没有空行/时间戳分隔。
        // 例如："you have a XX package ..." 连续出现两次，应该视为两条独立消息。
        val newMessageStarter = Regex(
            "^(you have|you've got|you\\s+have\\s+an|你有|您有).*(package|parcel|包裹|快递)",
            RegexOption.IGNORE_CASE
        )

        val blocks = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            val s = current.toString().trim()
            if (s.isNotBlank()) blocks.add(s)
            current.clear()
        }

        for (line in text.lines()) {
            val t = line.trim()
            val isSeparator = t.isEmpty() || timeStampLine.matches(t) || t.contains("SMS", ignoreCase = true)
            if (isSeparator) {
                flush()
            } else {
                // 如果这一行看起来是“新短信开头”，且当前块已有内容，则先切块
                if (current.isNotEmpty() && newMessageStarter.containsMatchIn(t)) {
                    flush()
                }
                if (current.isNotEmpty()) current.append('\n')
                current.append(t)
            }
        }
        flush()

        if (blocks.size <= 1) return text

        return buildString {
            blocks.forEachIndexed { idx, b ->
                append("短信 ")
                append(idx + 1)
                append(":\n")
                append(b)
                if (idx != blocks.lastIndex) append("\n\n")
            }
        }
    }

    fun extractTasksFromModelOutput(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()

        val normalized = raw
            .replace("\r\n", "\n")
            .trim()

        if (normalized.isBlank()) return emptyList()

        // 处理常见包装/前缀
        val stripped = normalized
            .removeSurrounding("\"", "\"")
            .trim()
            .removePrefix("输出：")
            .removePrefix("Output:")
            .removePrefix("Task:")
            .trim()

        // 去掉 markdown code fence（有些模型会包一层 ```）
        val unfenced = stripped
            .replace(Regex("(?s)^```[a-zA-Z0-9_-]*\\s*"), "")
            .replace(Regex("(?s)```$"), "")
            .trim()

        if (unfenced.isBlank()) return emptyList()

        // 明确无任务
        val lines = unfenced.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size == 1 && lines[0] == "无任务") return emptyList()
        if (lines.any { it == "无任务" } && lines.none { it.startsWith("## ") }) return emptyList()

        // 主路径：按 ## 标题切块
        val heading = Regex("(?m)^##\\s+")
        val matches = heading.findAll(unfenced).toList()
        if (matches.isNotEmpty()) {
            val starts = matches.map { it.range.first }
            val blocks = starts.mapIndexed { index, start ->
                val end = if (index + 1 < starts.size) starts[index + 1] else unfenced.length
                unfenced.substring(start, end).trim()
            }
            return blocks.filter { it.isNotBlank() && it != "无任务" }
        }

        // 兜底：按空行分段
        return unfenced
            .split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "无任务" }
    }
}
