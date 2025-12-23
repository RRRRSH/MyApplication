package com.RSS.todolist.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// 1. 定义数据模型：包含文本和完成状态
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

    // 获取任务列表 (返回的是 TodoTask 对象列表)
    fun getTasks(context: Context): MutableList<TodoTask> {
        val json = getPrefs(context).getString(KEY_TASKS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<TodoTask>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    // 添加任务 (默认未完成)
    fun addTask(context: Context, text: String) {
        val tasks = getTasks(context)
        tasks.add(TodoTask(text, false))
        saveTasks(context, tasks)
    }

    // 🌟 核心新功能：切换任务的完成状态
    fun toggleTaskCompletion(context: Context, index: Int) {
        val tasks = getTasks(context)
        if (index in tasks.indices) {
            val task = tasks[index]
            // 取反：如果已完成变未完成，反之亦然
            task.isCompleted = !task.isCompleted
            saveTasks(context, tasks)
        }
    }

    // 设置特定状态 (用于通知栏直接标记为完成)
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