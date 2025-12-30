package com.RSS.todolist.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

data class TodoTask(
    val text: String,
    var isCompleted: Boolean = false
)

object TaskStore {
    private const val PREF_NAME = "todo_list_pref"
    private const val KEY_TASKS = "tasks"
    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getTasks(context: Context): MutableList<TodoTask> {
        val json = getPrefs(context).getString(KEY_TASKS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<TodoTask>>() {}.type
        
        return try {
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: JsonSyntaxException) {
            // 兼容旧数据格式
            try {
                val oldType = object : TypeToken<MutableList<String>>() {}.type
                val oldList: MutableList<String>? = gson.fromJson(json, oldType)
                if (oldList != null) {
                    val newList = oldList.map { TodoTask(it, false) }.toMutableList()
                    saveTasks(context, newList)
                    return newList
                }
            } catch (e2: Exception) { }
            mutableListOf()
        }
    }

    // 新增任务
    fun addTask(context: Context, text: String) {
        val tasks = getTasks(context)
        tasks.add(TodoTask(text, false))
        saveTasks(context, tasks)
    }

    // 批量新增任务：一次性写入，返回新增的索引范围（用于增量通知更新）
    fun addTasks(context: Context, texts: List<String>): IntRange? {
        val clean = texts.map { it.trim() }.filter { it.isNotBlank() }
        if (clean.isEmpty()) return null

        val tasks = getTasks(context)
        val start = tasks.size
        clean.forEach { tasks.add(TodoTask(it, false)) }
        saveTasks(context, tasks)
        return start until tasks.size
    }

    // 🌟 新增：更新任务文字
    fun updateTask(context: Context, index: Int, newText: String) {
        val tasks = getTasks(context)
        if (index in tasks.indices) {
            tasks[index] = tasks[index].copy(text = newText)
            saveTasks(context, tasks)
        }
    }

    // 标记完成状态
    fun setTaskCompleted(context: Context, index: Int, completed: Boolean) {
        val tasks = getTasks(context)
        if (index in tasks.indices) {
            tasks[index].isCompleted = completed
            saveTasks(context, tasks)
        }
    }

    fun clearTasks(context: Context) {
        saveTasks(context, mutableListOf())
    }

    fun removeTask(context: Context, index: Int) {
        val tasks = getTasks(context)
        if (index in tasks.indices) {
            tasks.removeAt(index)
            saveTasks(context, tasks)
        }
    }

    private fun saveTasks(context: Context, tasks: List<TodoTask>) {
        val json = gson.toJson(tasks)
        getPrefs(context).edit().putString(KEY_TASKS, json).apply()
    }
}