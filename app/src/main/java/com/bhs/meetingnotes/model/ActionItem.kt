package com.bhs.meetingnotes.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ActionItem(
    val id: Int = 1,
    val task: String,
    val assignee: String = "Unassigned",
    val deadline: String = "N/A"
) {
    companion object {
        fun fromJson(raw: String): List<ActionItem> {
            if (raw.isBlank()) return emptyList()
            try {
                val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                if (cleaned.startsWith("[")) {
                    val type = object : TypeToken<List<ActionItem>>() {}.type
                    val list: List<ActionItem>? = Gson().fromJson(cleaned, type)
                    if (!list.isNullOrEmpty()) return list
                }
            } catch (_: Exception) {}

            // Fallback: parse numbered list text or raw text line by line
            val items = mutableListOf<ActionItem>()
            val lines = raw.split("\n")
            var id = 1
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed.matches("^\\d+[\\.\\)].*".toRegex())) {
                    val taskText = trimmed.replace("^\\d+[\\.\\)]\\s*".toRegex(), "")
                    items.add(ActionItem(id = id++, task = taskText, assignee = "Chưa rõ", deadline = "N/A"))
                }
            }
            if (items.isNotEmpty()) return items

            return listOf(ActionItem(id = 1, task = raw.trim(), assignee = "Chưa rõ", deadline = "N/A"))
        }

        fun toJson(items: List<ActionItem>): String {
            return try {
                Gson().toJson(items)
            } catch (e: Exception) {
                "[]"
            }
        }
    }
}