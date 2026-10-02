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
        fun fromJson(json: String): List<ActionItem> {
            return try {
                if (json.isBlank()) emptyList()
                else {
                    val type = object : TypeToken<List<ActionItem>>() {}.type
                    Gson().fromJson(json, type) ?: emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
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