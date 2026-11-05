package com.kafkasl.phonewhisper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saved transcriptions, newest first, in a private file inside the app.
 * By default anything older than 24 hours is deleted; the "Keep history forever"
 * setting (pref "keep_history_forever") turns that off.
 */
object HistoryStore {
    data class Entry(val time: Long, val text: String, val model: String)

    private const val FILE = "history.json"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private val lock = Any()

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    private fun keepForever(ctx: Context) =
        ctx.getSharedPreferences("phonewhisper", Context.MODE_PRIVATE).getBoolean("keep_history_forever", false)

    private fun read(ctx: Context): MutableList<Entry> = try {
        val arr = JSONArray(file(ctx).takeIf { it.exists() }?.readText() ?: "[]")
        MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Entry(o.getLong("time"), o.getString("text"), o.optString("model"))
        }
    } catch (e: Exception) {
        mutableListOf()
    }

    private fun write(ctx: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(JSONObject().put("time", it.time).put("text", it.text).put("model", it.model)) }
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file(ctx))
    }

    /** Drops entries older than 24 hours unless the person chose to keep history forever. */
    private fun pruned(ctx: Context, entries: List<Entry>): List<Entry> {
        if (keepForever(ctx)) return entries
        val cutoff = System.currentTimeMillis() - DAY_MS
        return entries.filter { it.time >= cutoff }
    }

    fun add(ctx: Context, text: String, model: String) = synchronized(lock) {
        val entries = read(ctx)
        entries.add(0, Entry(System.currentTimeMillis(), text, model))
        write(ctx, pruned(ctx, entries))
    }

    fun all(ctx: Context): List<Entry> = synchronized(lock) {
        val entries = read(ctx)
        val kept = pruned(ctx, entries)
        if (kept.size != entries.size) write(ctx, kept)
        kept
    }

    fun delete(ctx: Context, entry: Entry) = synchronized(lock) {
        write(ctx, read(ctx).filterNot { it.time == entry.time && it.text == entry.text })
    }

    fun clear(ctx: Context) = synchronized(lock) { file(ctx).delete(); Unit }
}
