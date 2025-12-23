package com.RSS.todolist.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TaskStore {
    private const val PREF_NAME = "todo_list_pref"
    private const val KEY_TASKS = "tasks"
    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取所有任务列表
     */
    fun getTasks(context: Context): MutableList<String> {
        val json = getPrefs(context).getString(KEY_TASKS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<String>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    /**
     * 添加一个新任务
     */
    fun addTask(context: Context, task: String) {
        val tasks = getTasks(context)
        tasks.add(task) // 追加到末尾
        saveTasks(context, tasks)
    }

    /**
     * 清空所有任务
     */
    fun clearTasks(context: Context) {
        saveTasks(context, mutableListOf())
    }

    /**
     * 移除指定任务（预留功能）
     */
    fun removeTask(context: Context, index: Int) {
        val tasks = getTasks(context)
        if (index >= 0 && index < tasks.size) {
            tasks.removeAt(index)
            saveTasks(context, tasks)
        }
    }

    private fun saveTasks(context: Context, tasks: List<String>) {
        val json = gson.toJson(tasks)
        getPrefs(context).edit().putString(KEY_TASKS, json).apply()
    }
}