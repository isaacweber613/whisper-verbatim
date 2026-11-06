package com.kafkasl.phonewhisper

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

/** Saved transcriptions. Tap one to copy it; long-press to delete it. */
class HistoryActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private lateinit var empty: TextView

    private val dp get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * dp).toInt()

    private fun attrColor(attr: Int): Int {
        val tv = TypedValue(); theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    private fun selectable(): android.graphics.drawable.Drawable? {
        val tv = TypedValue(); theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return ContextCompat.getDrawable(this, tv.resourceId)
    }

    private fun prefs() = getSharedPreferences("phonewhisper", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(TextView(this).apply {
            text = "History"
            textSize = 32f
            setPadding(dp(24), dp(64), dp(24), dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Tap a transcription to copy it. Long-press to delete it."
            textSize = 14f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            setPadding(dp(24), 0, dp(24), dp(16))
        })

        // Keep-forever switch
        val keepSwitch = MaterialSwitch(this).apply {
            isChecked = prefs().getBoolean("keep_history_forever", false)
            isClickable = false
        }
        val keepTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@HistoryActivity).apply { text = "Keep history forever"; textSize = 18f })
            addView(TextView(this@HistoryActivity).apply {
                text = "Off: transcriptions are deleted after 24 hours"
                textSize = 14f
                setTextColor(attrColor(android.R.attr.textColorSecondary))
            })
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = selectable()
            isFocusable = true
            addView(keepTexts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(keepSwitch)
            setOnClickListener {
                val newVal = !keepSwitch.isChecked
                if (!newVal && HistoryStore.all(this@HistoryActivity).isNotEmpty()) {
                    android.app.AlertDialog.Builder(this@HistoryActivity)
                        .setTitle("Turn off keep forever?")
                        .setMessage("Transcriptions older than 24 hours will be deleted right away.")
                        .setPositiveButton("Turn off") { _, _ ->
                            prefs().edit().putBoolean("keep_history_forever", false).apply()
                            keepSwitch.isChecked = false
                            render()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    prefs().edit().putBoolean("keep_history_forever", newVal).apply()
                    keepSwitch.isChecked = newVal
                    render()
                }
            }
        })

        // Clear all
        root.addView(TextView(this).apply {
            text = "Clear all history"
            textSize = 16f
            setTextColor(attrColor(com.google.android.material.R.attr.colorPrimary))
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = selectable()
            isFocusable = true
            setOnClickListener {
                android.app.AlertDialog.Builder(this@HistoryActivity)
                    .setTitle("Clear all history?")
                    .setMessage("This deletes every saved transcription.")
                    .setPositiveButton("Clear") { _, _ -> HistoryStore.clear(this@HistoryActivity); render() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })

        empty = TextView(this).apply {
            text = "No transcriptions yet."
            textSize = 15f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(empty)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(attrColor(android.R.attr.colorBackground))
            addView(root)
            // Android 15+ draws apps under the status and navigation bars; pad so nothing hides behind them.
            clipToPadding = false
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        })
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        list.removeAllViews()
        val entries = HistoryStore.all(this)
        empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        for (e in entries) {
            val whenText = DateUtils.getRelativeDateTimeString(
                this, e.time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(12), dp(24), dp(12))
                background = selectable()
                isFocusable = true
                addView(TextView(this@HistoryActivity).apply {
                    text = whenText
                    textSize = 12f
                    setTextColor(attrColor(android.R.attr.textColorSecondary))
                })
                addView(TextView(this@HistoryActivity).apply {
                    text = e.text
                    textSize = 16f
                    setTextIsSelectable(false)
                })
                setOnClickListener {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Transcription", e.text))
                    Toast.makeText(this@HistoryActivity, "Copied", Toast.LENGTH_SHORT).show()
                }
                setOnLongClickListener {
                    android.app.AlertDialog.Builder(this@HistoryActivity)
                        .setMessage("Delete this transcription?")
                        .setPositiveButton("Delete") { _, _ -> HistoryStore.delete(this@HistoryActivity, e); render() }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            })
        }
    }
}
