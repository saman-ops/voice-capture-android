package com.s3id3l.voicecapture.ui.detail

import org.json.JSONArray
import org.json.JSONObject

data class DetailActionItem(
    val text: String,
    val sentToTasks: Boolean = false,
    val done: Boolean = false
)

fun parseDetailActionItems(json: String): List<DetailActionItem> {
    if (json.isBlank() || json == "[]") return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val el = arr.get(i)
            if (el is JSONObject) {
                DetailActionItem(
                    text = el.getString("text"),
                    sentToTasks = el.optBoolean("sentToTasks", false),
                    done = el.optBoolean("done", false)
                )
            } else {
                DetailActionItem(text = el.toString())
            }
        }
    } catch (_: Exception) { emptyList() }
}

fun serializeDetailActionItems(items: List<DetailActionItem>): String {
    val arr = JSONArray()
    items.forEach { item ->
        arr.put(JSONObject().apply {
            put("text", item.text)
            put("sentToTasks", item.sentToTasks)
            put("done", item.done)
        })
    }
    return arr.toString()
}
