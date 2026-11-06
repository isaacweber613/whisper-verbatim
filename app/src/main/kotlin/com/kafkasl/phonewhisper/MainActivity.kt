package com.kafkasl.phonewhisper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusSubtitle: TextView
    private lateinit var audioRowSub: TextView
    private lateinit var accRowSub: TextView
    private lateinit var keepRunningRowSub: TextView
    private lateinit var updateRow: LinearLayout
    private lateinit var updateRowSub: TextView
    private var update: Updater.Release? = null
    private lateinit var updateAction: UpdateAction
    private var updateChecking = false
    private lateinit var updateNotifySwitch: MaterialSwitch
    private lateinit var vibrateSwitch: MaterialSwitch

    // Export and import of all settings, including the API key (SettingsBackup.kt).
    private val exportLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) exportSettings(uri)
    }
    private val importLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importSettings(uri)
    }
    private lateinit var updateNotifyRowSub: TextView
    private lateinit var keyRowSub: TextView
    private lateinit var cloudModelRow: LinearLayout
    private lateinit var cloudModelRowSub: TextView
    private lateinit var verbatimRow: LinearLayout
    private lateinit var languageRow: LinearLayout
    private lateinit var languageRowSub: TextView
    private lateinit var historyRowSub: TextView
    private lateinit var promptRowSub: TextView
    private lateinit var promptRow: LinearLayout
    private lateinit var modelContainer: LinearLayout
    private lateinit var promptContainer: LinearLayout
    private lateinit var shortcutSwitch: MaterialSwitch
    private lateinit var shortcutRowSub: TextView
    private lateinit var shortcutKeysRow: LinearLayout
    private lateinit var shortcutKeysRowSub: TextView
    private lateinit var shortcutHoldRow: LinearLayout
    private lateinit var shortcutHoldRowSub: TextView
    private lateinit var shortcutBlockRow: LinearLayout
    private lateinit var shortcutBlockSwitch: MaterialSwitch
    private lateinit var shortcutBlockRowSub: TextView

    // Setup walkthrough state
    private var setupDialog: android.app.AlertDialog? = null
    private var setupSnoozed = false
    private var audioAsked = false
    private var setupWasIncomplete = false
    private var setupDialogStep: SetupStep? = null

    private val modelRows = mutableMapOf<String, ModelRowViews>()
    private val promptRows = mutableMapOf<String, PromptRowViews>()

    private data class ModelRowViews(
        val radio: MaterialRadioButton,
        val progress: LinearProgressIndicator,
        val subtitle: TextView,
        val dlBtn: MaterialButton
    )

    private data class PromptRowViews(
        val radio: MaterialRadioButton,
        val subtitle: TextView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = vertical(0, 0)

        // Top large header (like "Connected devices")
        val header = TextView(this).apply {
            text = "Word for Word"
            textSize = 32f
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        root.addView(header)

        // Status row: tapping it restarts the setup walkthrough
        val statusRow = settingsRow("Status", "Checking...") {
            setupSnoozed = false
            maybeRunSetup()
        }
        statusSubtitle = statusRow.findViewWithTag("subtitle")
        root.addView(statusRow)

        // Shown only when GitHub has a newer release: tap to download, then tap to install.
        updateRow = settingsRow("Update", "") { onUpdateTapped() }
        updateRowSub = updateRow.findViewWithTag("subtitle")
        updateAction = UpdateAction(this, updateRowSub)
        updateRow.visibility = View.GONE
        root.addView(updateRow)

        // --- Setup Section ---
        root.addView(sectionHeader("Setup"))
        
        val audioRow = settingsRow("Audio permission", "Checking...") {
            if (!hasPerm(Manifest.permission.RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
        }
        audioRowSub = audioRow.findViewWithTag("subtitle")
        root.addView(audioRow)

        val accRow = settingsRow("Accessibility service", "Checking...") {
            setupSnoozed = false
            if (WhisperAccessibilityService.instance == null) showAccessibilityStep()
            else openAccessibilitySettings()
        }
        accRowSub = accRow.findViewWithTag("subtitle")
        root.addView(accRow)

        val keepRunningRow = settingsRow("Keep running", "") { showKeepRunning() }
        keepRunningRowSub = keepRunningRow.findViewWithTag("subtitle")
        root.addView(keepRunningRow)

        // --- Engine Section ---
        
        val isCloud = !prefs().getBoolean("use_local", false)
        
        val cloudSwitch = MaterialSwitch(this).apply {
            isChecked = isCloud
            isClickable = false
        }
        val cloudRow = settingsRow("Use cloud transcription", "Requires OpenRouter API key", cloudSwitch) {
            val newCloud = !cloudSwitch.isChecked
            prefs().edit().putBoolean("use_local", !newCloud).apply()
            cloudSwitch.isChecked = newCloud
            refresh()
        }
        // Cloud-only fork: the cloud switch and local models stay out of the screen, and
        // any old "local" choice from an earlier version is reset.
        prefs().edit().putBoolean("use_local", false).apply()
        cloudRow.visibility = View.GONE

        cloudModelRow = settingsRow("Cloud model", "") { pickCloudModel() }
        cloudModelRowSub = cloudModelRow.findViewWithTag("subtitle")
        root.addView(cloudModelRow)

        val verbatimSwitch = MaterialSwitch(this).apply {
            isChecked = prefs().getBoolean("verbatim", false)
            isClickable = false
        }
        verbatimRow = settingsRow("Verbatim mode",
            "On: asks the model to keep \"um,\" \"uh,\" repeats, and false starts. Off: the model's own default, which may tidy them up",
            verbatimSwitch) {
            val newVal = !verbatimSwitch.isChecked
            prefs().edit().putBoolean("verbatim", newVal).apply()
            verbatimSwitch.isChecked = newVal
        }
        root.addView(verbatimRow)

        languageRow = settingsRow("Language", "") { pickLanguage() }
        languageRowSub = languageRow.findViewWithTag("subtitle")
        root.addView(languageRow)

        // --- History ---
        root.addView(sectionHeader("History"))
        val historyRow = settingsRow("Transcription history", "") {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        historyRowSub = historyRow.findViewWithTag("subtitle")
        root.addView(historyRow)

        // Local Models section
        modelContainer = vertical(0)
        modelContainer.addView(sectionHeader("Local models"))
        for (m in MODEL_CATALOG) modelContainer.addView(buildModelRow(m))
        // Not added to the screen: this fork doesn't ship the on-phone speech engine.

        // --- Post-Processing (removed from the screen) ---
        // This fork never rewrites your words, so the cleanup option is gone and any
        // earlier "on" setting is turned off. The views below are built but not shown.
        prefs().edit().putBoolean("use_post_processing", false).apply()

        promptContainer = vertical(0)
        for (preset in promptPresets()) promptContainer.addView(buildPromptRow(preset))

        promptRow = settingsRow("Edit current prompt", currentPrompt()) { promptPostProcessing() }
        promptRowSub = promptRow.findViewWithTag("subtitle")
        promptRowSub.maxLines = 2
        promptRowSub.ellipsize = android.text.TextUtils.TruncateAt.END

        // --- Settings Section ---
        root.addView(sectionHeader("Settings"))
        
        val keyRow = settingsRow("OpenRouter API Key", "Tap to set") { promptApiKey() }
        keyRowSub = keyRow.findViewWithTag("subtitle")
        root.addView(keyRow)

        vibrateSwitch = MaterialSwitch(this).apply { isClickable = false }
        root.addView(settingsRow("Vibrate", "A short buzz when recording starts and stops", vibrateSwitch) {
            prefs().edit().putBoolean("vibrate", !prefs().getBoolean("vibrate", true)).apply()
            refresh()
        })

        updateNotifySwitch = MaterialSwitch(this).apply { isClickable = false }
        val updateNotifyRow = settingsRow("Update notifications", "", updateNotifySwitch) {
            val on = !UpdateNotifier.enabled(this)
            UpdateNotifier.setEnabled(this, on)
            // Android 13+ asks once whether the app may show notifications.
            if (on && android.os.Build.VERSION.SDK_INT >= 33 &&
                !hasPerm(Manifest.permission.POST_NOTIFICATIONS)) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            }
            refresh()
        }
        updateNotifyRowSub = updateNotifyRow.findViewWithTag("subtitle")
        root.addView(updateNotifyRow)

        root.addView(settingsRow("Export settings", "Save all settings, including your API key, to a file") {
            exportLauncher.launch("word-for-word-settings.json")
        })
        root.addView(settingsRow("Import settings", "Load settings from a file you exported") {
            importLauncher.launch(arrayOf("*/*"))
        })

        // --- Keyboard shortcut, last on the page so touch-screen users never need it ---
        val shortcutSection = vertical(0)
        root.addView(shortcutSection)
        shortcutSection.addView(sectionHeader("Keyboard shortcut (for phones with a physical keyboard)"))
        shortcutSwitch = MaterialSwitch(this).apply {
            isChecked = prefs().getBoolean("shortcut_enabled", false)
            isClickable = false
        }
        val shortcutRow = settingsRow("Start and stop talking", "", shortcutSwitch) {
            when {
                shortcutSwitch.isChecked -> setShortcutEnabled(false)
                Shortcut.keys(prefs()).isEmpty() -> captureShortcut { setShortcutEnabled(true) }
                else -> setShortcutEnabled(true)
            }
        }
        shortcutRowSub = shortcutRow.findViewWithTag("subtitle")
        shortcutSection.addView(shortcutRow)
        shortcutKeysRow = settingsRow("Shortcut keys", "") { captureShortcut(null) }
        shortcutKeysRowSub = shortcutKeysRow.findViewWithTag("subtitle")
        shortcutSection.addView(shortcutKeysRow)
        shortcutHoldRow = settingsRow("How long to press", "") { pickHoldTime() }
        shortcutHoldRowSub = shortcutHoldRow.findViewWithTag("subtitle")
        shortcutSection.addView(shortcutHoldRow)
        // Shown only for a one-key shortcut; two-key shortcuts never block their keys.
        shortcutBlockSwitch = MaterialSwitch(this).apply { isClickable = false }
        shortcutBlockRow = settingsRow("Turn off the key's normal job", "", shortcutBlockSwitch) {
            prefs().edit().putBoolean("shortcut_block_key", !shortcutBlockSwitch.isChecked).apply()
            WhisperAccessibilityService.instance?.applyShortcutSettings()
            refresh()
        }
        shortcutBlockRowSub = shortcutBlockRow.findViewWithTag("subtitle")
        shortcutSection.addView(shortcutBlockRow)

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

        // The setup walkthrough (maybeRunSetup) asks for the microphone, so no request here.
        refresh()
    }

    override fun onResume() {
        super.onResume()
        // The service can finish turning on a moment after you return from Settings; update when it does.
        WhisperAccessibilityService.onStateChanged = { refresh(); maybeRunSetup() }
        refresh(); maybeRunSetup()
        // Reopening from recent apps doesn't restart the screen, so check here, not only at start.
        checkForUpdate()
    }

    override fun onPause() {
        WhisperAccessibilityService.onStateChanged = null
        super.onPause()
    }
    override fun onRequestPermissionsResult(c: Int, p: Array<String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r); refresh(); maybeRunSetup()
    }

    // --- Setup walkthrough ---

    /** What's still missing, in the order the walkthrough asks for it. */
    private enum class SetupStep { MIC, ACCESSIBILITY, API_KEY, DONE }

    private fun nextSetupStep(): SetupStep {
        val useLocal = prefs().getBoolean("use_local", false)
        val usePostProcessing = prefs().getBoolean("use_post_processing", false)
        val hasKey = !prefs().getString("api_key", "").isNullOrBlank()
        return when {
            !hasPerm(Manifest.permission.RECORD_AUDIO) -> SetupStep.MIC
            WhisperAccessibilityService.instance == null -> SetupStep.ACCESSIBILITY
            (!useLocal || usePostProcessing) && !hasKey -> SetupStep.API_KEY
            else -> SetupStep.DONE
        }
    }

    /**
     * Shows the next setup step as a dialog. Runs every time the app comes back to the
     * front, so after the person turns something on in Android settings and returns,
     * the next step appears on its own. "Later" stops it until the app is reopened
     * or the Status row is tapped.
     */
    private fun maybeRunSetup() {
        if (isFinishing || setupSnoozed) return
        val step = nextSetupStep()
        // A dialog for this same step is already up: leave it. If the person finished that
        // step some other way (say, turned accessibility on by hand), swap in the next one.
        if (setupDialog?.isShowing == true) {
            if (step == setupDialogStep) return
            setupDialog?.dismiss()
        }
        setupDialogStep = step
        if (step == SetupStep.DONE) {
            if (setupWasIncomplete) {
                setupWasIncomplete = false
                Toast.makeText(this, "Setup complete. Tap the floating dot in any app to dictate.", Toast.LENGTH_LONG).show()
            }
            return
        }
        setupWasIncomplete = true
        when (step) {
            SetupStep.MIC -> showMicStep()
            SetupStep.ACCESSIBILITY -> showAccessibilityStep()
            SetupStep.API_KEY -> promptApiKey(fromSetup = true)
            SetupStep.DONE -> Unit
        }
    }

    private fun setupBuilder(stepNumber: Int, title: String) =
        android.app.AlertDialog.Builder(this)
            .setTitle("Step $stepNumber of 2: $title")
            .setCancelable(false)

    /**
     * Microphone: asks with Android's own prompt the moment the app opens, with no
     * dialog of ours first. After a "Don't allow", a dialog offers to ask again, or
     * sends the person to App info once Android refuses to show its prompt.
     */
    private fun showMicStep() {
        if (!audioAsked) {
            audioAsked = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        // Denied this session. Android shows its own prompt again only if it still allows asking.
        val canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
        setupDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Microphone needed")
            .setMessage(
                if (canAskAgain) "Word for Word needs the microphone to record what you say. Tap Ask again and choose \"While using the app.\""
                else "Word for Word needs the microphone to record what you say. Android won't ask again, so tap Open App info, tap Permissions, tap Microphone, and choose \"Allow only while using the app.\"")
            .setCancelable(false)
            .setPositiveButton(if (canAskAgain) "Ask again" else "Open App info") { _, _ ->
                if (canAskAgain) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                else openAppInfo()
            }
            .setNegativeButton("Later") { _, _ -> setupSnoozed = true }
            .show()
    }

    /**
     * Accessibility, in the order Android forces on sideloaded apps:
     *   1. Accessibility settings: try to turn it on (Android blocks it as a "restricted setting").
     *   2. App info: three-dot menu, Allow restricted settings.
     *   3. Accessibility settings again: turn it on for real.
     * The phase is saved, so each return to the app shows the next part. If the
     * service turns on at any point (some phones never block it), the walkthrough moves on.
     */
    private fun showAccessibilityStep() {
        // Google Play requires this disclosure, and a clear yes, before sending anyone to turn the service on.
        if (!prefs().getBoolean("acc_disclosure_agreed", false)) { showAccessibilityDisclosure(); return }
        val phase = prefs().getInt("acc_setup_phase", 0)
        val b = setupBuilder(1, "Accessibility (part ${phase + 1} of 3)")
        when (phase) {
            0 -> b.setMessage("Tap Word for Word. Android will block it. Tap Close and come back.")
                .setPositiveButton("Open Accessibility") { _, _ ->
                    setAccPhase(1); openAccessibilitySettings()
                    // Shows over Settings. Android blocks reminders from a background app with notifications off,
                    // so this is the only one.
                    Toast.makeText(this, "When Android blocks it, tap Close and come back", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Later") { _, _ -> setupSnoozed = true }
            // If Android didn't block it, the service is on and the walkthrough has moved on.
            1 -> b.setMessage("Tap ⋮ at the top right, then Allow restricted settings. Then come back.")
                .setPositiveButton("Open App info") { _, _ ->
                    setAccPhase(2); openAppInfo()
                    Toast.makeText(this, "Tap ⋮, then Allow restricted settings. Then come back", Toast.LENGTH_LONG).show()
                }
            else -> b.setMessage("Last part. Turn on Word for Word.")
                .setPositiveButton("Open Accessibility") { _, _ -> openAccessibilitySettings() }
                // In case it's still blocked: back to Allow restricted settings.
                .setNeutralButton("App info") { _, _ -> openAppInfo() }
                .setNegativeButton("Later") { _, _ -> setupSnoozed = true }
        }
        setupDialog = b.show()
    }

    /**
     * The phone maker's battery and auto-start settings, so Android doesn't stop the service
     * (see [BackgroundSettings]). Each step opens its settings screen.
     */
    private fun showKeepRunning() {
        val steps = BackgroundSettings.steps(BackgroundSettings.vendor(), packageName)
        val items = steps.mapIndexed { i, step ->
            val done = step.status(this) == BackgroundSettings.Status.DONE
            "${i + 1}. ${step.title}\n" + (if (done) "✓ Done" else step.howTo)
        }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Keep running")
            .setItems(items) { _, which -> BackgroundSettings.open(this, steps[which]) }
            .setNegativeButton("Close", null)
            .show()
    }

    // --- In-app updates ---

    private fun currentVersion(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: "0"

    private fun checkForUpdate() {
        if (updateChecking) return
        updateChecking = true
        val current = currentVersion()
        Thread {
            val release = Updater.checkForUpdate(current)
            runOnUiThread { updateChecking = false }
            if (release == null) return@Thread
            runOnUiThread {
                update = release
                (updateRow.getChildAt(0) as? LinearLayout)?.let { texts ->
                    (texts.getChildAt(0) as? TextView)?.text = "Update to ${release.version}"
                }
                updateRowSub.text = "Tap to download"
                updateRow.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun onUpdateTapped() {
        val release = update ?: return
        updateAction.onTap(release)
    }

    private fun exportSettings(uri: android.net.Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(SettingsBackup.export(prefs().all).toByteArray()) }
            Toast.makeText(this, "Settings saved. The file includes your API key, so keep it private.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't save the settings file.", Toast.LENGTH_LONG).show()
        }
    }

    private fun importSettings(uri: android.net.Uri) {
        val values = try {
            contentResolver.openInputStream(uri)?.use { SettingsBackup.parse(it.readBytes().toString(Charsets.UTF_8)) }
        } catch (e: Exception) {
            null
        }
        if (values == null) {
            Toast.makeText(this, "That isn't a Word for Word settings file.", Toast.LENGTH_LONG).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Import settings?")
            .setMessage("This replaces all your current settings, including your API key.")
            .setPositiveButton("Import") { _, _ ->
                SettingsBackup.apply(prefs(), values)
                WhisperAccessibilityService.instance?.applyShortcutSettings()
                UpdateNotifier.schedule(this)
                refresh()
                Toast.makeText(this, "Settings imported", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setAccPhase(phase: Int) = prefs().edit().putInt("acc_setup_phase", phase).apply()

    /**
     * The "prominent disclosure" Google Play requires for apps that use the accessibility
     * service: what the app can see, what it does with it, and an explicit Agree button.
     */
    private fun showAccessibilityDisclosure() {
        setupDialog = setupBuilder(1, "Accessibility")
            .setMessage(
                "Word for Word uses Accessibility to show the mic button and type your words into the text box you're in. " +
                "It reads only that text box, and never saves or shares it. " +
                "If you turn on the keyboard shortcut, it also watches for that key press. " +
                "Your recording goes only to OpenRouter, with your own key.")
            .setPositiveButton("Agree") { _, _ ->
                prefs().edit().putBoolean("acc_disclosure_agreed", true).apply()
                showAccessibilityStep()
            }
            .setNegativeButton("No thanks") { _, _ -> setupSnoozed = true }
            .show()
    }

    /**
     * Opens Android's accessibility list with Word for Word highlighted. (Jumping straight
     * to the app's own page needs a permission only system apps get, so the list is as close as
     * a regular app can go.)
     */
    private fun openAccessibilitySettings() {
        val key = android.content.ComponentName(this, WhisperAccessibilityService::class.java).flattenToString()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", key)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", key) })
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open Accessibility settings. Open Settings, then Accessibility.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", packageName, null)))
    }

    // --- Model Rows ---

    private fun buildModelRow(model: Model): View {
        val radio = MaterialRadioButton(this).apply {
            isClickable = false
            buttonTintList = ColorStateList.valueOf(attrColor(com.google.android.material.R.attr.colorPrimary))
        }
        val dlBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
            text = "↓"
            textSize = 18f
            setTextColor(attrColor(com.google.android.material.R.attr.colorPrimary))
        }
        
        val progress = LinearProgressIndicator(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LP_MATCH, dp(4)).apply {
                topMargin = dp(8)
            }
        }

        val rightContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dlBtn)
            addView(radio)
        }

        val row = settingsRow(
            if (model.recommended) model.name else model.name,
            "${model.quality} · ${model.sizeMb} MB",
            rightContainer
        ) {
            onModelAction(model)
        }
        
        val textContainer = row.getChildAt(0) as LinearLayout
        textContainer.addView(progress)
        
        modelRows[model.archive] = ModelRowViews(
            radio, progress, textContainer.findViewWithTag("subtitle"), dlBtn
        )
        refreshCard(model)
        
        return row
    }

    private fun onModelAction(model: Model) {
        val views = modelRows[model.archive] ?: return

        if (ModelDownloader.isInstalled(this, model)) {
            selectModel(model.archive)
            return
        }

        views.dlBtn.isEnabled = false
        views.progress.visibility = View.VISIBLE
        views.progress.isIndeterminate = false
        views.subtitle.text = "Starting download..."

        ModelDownloader.download(this, model) { state ->
            runOnUiThread {
                when (state) {
                    is DownloadState.Downloading -> {
                        views.progress.progress = (state.progress * 100).toInt()
                        views.subtitle.text = "Downloading: ${(state.progress * 100).toInt()}%"
                    }
                    is DownloadState.Extracting -> {
                        views.progress.isIndeterminate = true
                        views.subtitle.text = "Extracting..."
                    }
                    is DownloadState.Done -> {
                        views.progress.visibility = View.GONE
                        selectModel(model.archive)
                        toast("${model.name} ready!")
                    }
                    is DownloadState.Error -> {
                        views.progress.visibility = View.GONE
                        views.subtitle.text = "Error: ${state.message}"
                        views.dlBtn.isEnabled = true
                    }
                }
            }
        }
    }

    private fun selectModel(archive: String) {
        prefs().edit().putString("model_name", archive).apply()
        WhisperAccessibilityService.instance?.reloadModel()
        refreshAllCards(); refresh()
    }

    private fun refreshCard(model: Model) {
        val views = modelRows[model.archive] ?: return
        val active = prefs().getString("model_name", "") == model.archive
        val installed = ModelDownloader.isInstalled(this, model)
        
        views.radio.isChecked = active
        views.radio.visibility = if (installed) View.VISIBLE else View.GONE
        views.dlBtn.visibility = if (installed) View.GONE else View.VISIBLE
        
        if (views.progress.visibility == View.GONE) {
            views.subtitle.text = "${model.quality} · ${model.sizeMb} MB"
        }
    }

    private fun refreshAllCards() = MODEL_CATALOG.forEach { refreshCard(it) }

    // --- Prompt Rows ---

    private fun buildPromptRow(preset: PromptPreset): View {
        val radio = MaterialRadioButton(this).apply {
            isClickable = false
            buttonTintList = ColorStateList.valueOf(attrColor(com.google.android.material.R.attr.colorPrimary))
        }

        val row = settingsRow(preset.title, preset.subtitle, radio) {
            selectPrompt(preset.key)
        }

        promptRows[preset.key] = PromptRowViews(radio, row.findViewWithTag("subtitle"))
        refreshPromptRow(preset)
        return row
    }

    private fun selectPrompt(key: String) {
        val prompt = when (key) {
            "custom" -> customPrompt()
            else -> promptPresets().firstOrNull { it.key == key }?.prompt
        } ?: return
        prefs().edit().putString("post_processing_prompt", prompt).apply()
        refreshPromptRows(); refresh()
    }

    private fun refreshPromptRow(preset: PromptPreset) {
        val views = promptRows[preset.key] ?: return
        val current = currentPrompt()
        val active = when (preset.key) {
            "custom" -> current != PostProcessor.DEV_PROMPT && current != PostProcessor.SIMPLE_PROMPT
            else -> current == preset.prompt
        }
        views.radio.isChecked = active
        views.subtitle.text = if (preset.key == "custom") customPromptSummary() else preset.subtitle
    }

    private fun refreshPromptRows() = promptPresets().forEach { refreshPromptRow(it) }

    // --- State Updates ---

    private fun refresh() {
        val audio = hasPerm(Manifest.permission.RECORD_AUDIO)
        val acc = WhisperAccessibilityService.instance != null
        val useLocal = prefs().getBoolean("use_local", false)
        val usePostProcessing = prefs().getBoolean("use_post_processing", false)
        val hasKey = !prefs().getString("api_key", "").isNullOrBlank()
        val hasModel = LocalTranscriber.availableModels(this).isNotEmpty()

        audioRowSub.text = if (audio) "Granted" else "Tap to grant permission"
        accRowSub.text = if (acc) "Enabled" else "Tap to enable in settings"
        val keepSteps = BackgroundSettings.steps(BackgroundSettings.vendor(), packageName)
        keepRunningRowSub.text =
            if (keepSteps.all { it.status(this) == BackgroundSettings.Status.DONE }) "Done"
            else "Battery settings so Android doesn't turn off the mic button"

        modelContainer.visibility = if (useLocal) View.VISIBLE else View.GONE
        cloudModelRow.visibility = if (useLocal) View.GONE else View.VISIBLE
        verbatimRow.visibility = if (useLocal) View.GONE else View.VISIBLE
        val historyCount = HistoryStore.all(this).size
        val keepForever = prefs().getBoolean("keep_history_forever", false)
        historyRowSub.text = "$historyCount saved · " + (if (keepForever) "kept forever" else "kept 24 hours") + ". Tap to view and copy"
        val cloudModel = prefs().getString("cloud_model", TranscriberClient.DEFAULT_MODEL) ?: TranscriberClient.DEFAULT_MODEL
        cloudModelRowSub.text = TranscriberClient.summaryFor(cloudModel)
        // Only models that accept a language show the row.
        val modelLanguages = TranscriberClient.languagesFor(cloudModel)
        languageRow.visibility = if (useLocal || modelLanguages.isEmpty()) View.GONE else View.VISIBLE
        val language = prefs().getString("language", "auto") ?: "auto"
        languageRowSub.text = when {
            language in modelLanguages -> TranscriberClient.LANGUAGES[language]
            language == "auto" -> "Auto: the model guesses"
            else -> "Auto: no ${TranscriberClient.LANGUAGES[language]} on this model"
        }
        promptContainer.visibility = if (usePostProcessing) View.VISIBLE else View.GONE
        promptRow.visibility = if (usePostProcessing) View.VISIBLE else View.GONE

        val apiKey = prefs().getString("api_key", "") ?: ""
        keyRowSub.text = if (apiKey.isBlank()) "Tap to set" 
                         else if (apiKey.length > 7) "${apiKey.take(6)}...${apiKey.takeLast(4)}" 
                         else "Set"

        val prompt = currentPrompt()
        promptRowSub.text = prompt

        val cur = prefs().getString("model_name", "") ?: ""
        if (cur.isBlank() || !File(filesDir, "models/$cur").exists()) {
            MODEL_CATALOG.firstOrNull { ModelDownloader.isInstalled(this, it) }
                ?.let { selectModel(it.archive) }
        }

        // Ready logic
        val localReady = useLocal && hasModel
        val cloudReady = !useLocal && hasKey
        val postReady = !usePostProcessing || hasKey
        val ready = audio && acc && (localReady || cloudReady) && postReady

        val shortcutOn = Shortcut.enabled(prefs())
        statusSubtitle.text = when {
            !ready -> "Setup required. Tap here to continue"
            shortcutOn -> "Ready. Press ${Shortcut.describe(Shortcut.keys(prefs()))} in any app to dictate"
            else -> "Ready. Tap the floating dot in any app to dictate"
        }
        statusSubtitle.setTextColor(if (ready) attrColor(com.google.android.material.R.attr.colorPrimary) else attrColor(android.R.attr.textColorSecondary))
        
        vibrateSwitch.isChecked = prefs().getBoolean("vibrate", true)
        val notifyOn = UpdateNotifier.enabled(this)
        updateNotifySwitch.isChecked = notifyOn
        updateNotifyRowSub.text = when {
            !notifyOn -> "Off"
            android.os.Build.VERSION.SDK_INT >= 33 && !hasPerm(Manifest.permission.POST_NOTIFICATIONS) ->
                "On, but notifications are blocked. Allow them for Word for Word in Android settings"
            else -> "On: a notification when a new version is out"
        }

        shortcutSwitch.isChecked = shortcutOn
        shortcutRowSub.text = if (shortcutOn) "On. The mic button shows only while you talk" else "Off"
        shortcutKeysRow.visibility = if (shortcutOn) View.VISIBLE else View.GONE
        shortcutHoldRow.visibility = if (shortcutOn) View.VISIBLE else View.GONE
        shortcutKeysRowSub.text = Shortcut.describe(Shortcut.keys(prefs()))
        shortcutHoldRowSub.text = Shortcut.describeHold(Shortcut.holdMs(prefs()))
        val oneKey = Shortcut.keys(prefs()).size == 1
        val block = prefs().getBoolean("shortcut_block_key", false)
        shortcutBlockRow.visibility = if (shortcutOn && oneKey) View.VISIBLE else View.GONE
        shortcutBlockSwitch.isChecked = block
        shortcutBlockRowSub.text = if (block) "On: the key only starts and stops talking"
                                   else "Off: the key also does its normal job"

        refreshAllCards()
        refreshPromptRows()
    }

    // --- Keyboard shortcut ---

    private fun setShortcutEnabled(on: Boolean) {
        prefs().edit().putBoolean("shortcut_enabled", on).apply()
        WhisperAccessibilityService.instance?.applyShortcutSettings()
        refresh()
    }

    /**
     * Listens for the key or keys you press and offers to save them. While it listens,
     * every key press goes to this dialog; once it has caught a shortcut, the keys move
     * between the buttons normally again.
     */
    private fun captureShortcut(onSaved: (() -> Unit)?) {
        val ask = "Press the key or keys you want to use, then let go. " +
            "The keys keep working as usual."
        val held = mutableSetOf<Int>()
        val pressed = mutableSetOf<Int>()
        var caught: Set<Int>? = null
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Set shortcut")
            .setMessage(ask)
            .setPositiveButton("Save", null)
            .setNeutralButton("Try again", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnKeyListener { _, code, ev ->
            if (caught != null) return@setOnKeyListener false
            when (ev.action) {
                android.view.KeyEvent.ACTION_DOWN -> if (ev.repeatCount == 0) { held += code; pressed += code }
                android.view.KeyEvent.ACTION_UP -> {
                    held -= code
                    if (held.isEmpty() && pressed.isNotEmpty()) {
                        caught = pressed.toSet()
                        dialog.setMessage("Shortcut: ${Shortcut.describe(pressed)}")
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).apply { isEnabled = true; requestFocus() }
                    }
                }
            }
            true
        }
        dialog.setOnDismissListener { WhisperAccessibilityService.capturingShortcut = false }
        WhisperAccessibilityService.capturingShortcut = true
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = false
            setOnClickListener {
                val keys = caught ?: return@setOnClickListener
                prefs().edit().putString("shortcut_keys", Shortcut.encode(keys)).apply()
                dialog.dismiss()
                WhisperAccessibilityService.instance?.applyShortcutSettings()
                refresh()
                onSaved?.invoke()
            }
        }
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            caught = null; held.clear(); pressed.clear()
            dialog.setMessage(ask)
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }
    }

    private fun pickHoldTime() {
        val presets = listOf(0L, 500L, 1000L)
        val names = (presets.map { Shortcut.describeHold(it) } + "Custom").toTypedArray()
        val current = presets.indexOf(Shortcut.holdMs(prefs())).let { if (it < 0) presets.size else it }
        android.app.AlertDialog.Builder(this)
            .setTitle("How long to press")
            .setSingleChoiceItems(names, current) { d, which ->
                d.dismiss()
                if (which < presets.size) saveHoldTime(presets[which]) else promptCustomHoldTime()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptCustomHoldTime() {
        val input = EditText(this).apply {
            hint = "Seconds, for example 1.5"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(Shortcut.formatSeconds(Shortcut.holdMs(prefs())))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("How long to press")
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Save") { _, _ ->
                val seconds = input.text.toString().trim().toDoubleOrNull()
                if (seconds == null || seconds < 0) toast("Type a number of seconds, like 1.5")
                else saveHoldTime((seconds * 1000).toLong())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveHoldTime(ms: Long) {
        prefs().edit().putLong("shortcut_hold_ms", ms).apply()
        WhisperAccessibilityService.instance?.applyShortcutSettings()
        refresh()
    }

    private fun promptApiKey(fromSetup: Boolean = false) {
        val input = EditText(this).apply {
            hint = "sk-or-v1-..."
            isSingleLine = true
            setText(prefs().getString("api_key", ""))
        }
        val builder = if (fromSetup) setupBuilder(2, "OpenRouter API key")
                      else android.app.AlertDialog.Builder(this).setTitle("OpenRouter API Key")
        val dialog = builder
            .setMessage("Paste your OpenRouter API key. It starts with sk-or-v1. Don't have one? Tap Get a key.")
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Save") { _, _ ->
                val key = input.text.toString().trim()
                prefs().edit().putString("api_key", key).apply()
                refresh()
                if (key.isNotEmpty()) verifyKey(key)
                if (fromSetup) maybeRunSetup()
            }
            .setNeutralButton("Get a key") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://openrouter.ai/keys")))
            }
            .setNegativeButton(if (fromSetup) "Later" else "Cancel") { _, _ ->
                if (fromSetup) setupSnoozed = true
            }
            .show()
        if (fromSetup) setupDialog = dialog
    }

    /** Asks OpenRouter whether the saved key works, and says so either way. */
    private fun verifyKey(key: String) {
        keyRowSub.text = "Checking key..."
        TranscriberClient.checkKey(key) { problem ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                refresh()
                if (problem == null) {
                    Toast.makeText(this, "Key works. OpenRouter accepted it.", Toast.LENGTH_SHORT).show()
                } else {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Key problem")
                        .setMessage(problem)
                        .setPositiveButton("Fix key") { _, _ -> promptApiKey() }
                        .setNegativeButton("Close", null)
                        .show()
                }
            }
        }
    }

    /**
     * Model picker: each row shows the name, company, and price, plus an (i) button
     * for the details. The last row takes any OpenRouter model ID you paste.
     */
    private fun pickCloudModel() {
        val current = prefs().getString("cloud_model", TranscriberClient.DEFAULT_MODEL) ?: TranscriberClient.DEFAULT_MODEL
        val isCustom = TranscriberClient.CLOUD_MODELS.none { it.id == current }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        var dialog: android.app.AlertDialog? = null

        fun row(title: String, subtitle: String, selected: Boolean, info: String?,
                onDelete: (() -> Unit)? = null, onPick: () -> Unit): View {
            val radio = MaterialRadioButton(this).apply {
                isChecked = selected
                isClickable = false
                buttonTintList = ColorStateList.valueOf(attrColor(com.google.android.material.R.attr.colorPrimary))
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply { text = title; textSize = 16f })
                addView(TextView(this@MainActivity).apply {
                    text = subtitle; textSize = 13f
                    setTextColor(attrColor(android.R.attr.textColorSecondary))
                })
            }
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(8), dp(10))
                isClickable = true
                isFocusable = true
                background = selectableBackground()
                setOnClickListener { onPick() }
                addView(radio)
                addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (info != null) {
                    addView(TextView(this@MainActivity).apply {
                        text = "ⓘ"
                        textSize = 22f
                        gravity = Gravity.CENTER
                        setTextColor(attrColor(com.google.android.material.R.attr.colorPrimary))
                        setPadding(dp(12), dp(4), dp(12), dp(4))
                        isClickable = true
                        isFocusable = true
                        background = selectableBackground()
                        contentDescription = "About $title"
                        setOnClickListener {
                            android.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle(title)
                                .setMessage(info)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    })
                }
                if (onDelete != null) {
                    addView(TextView(this@MainActivity).apply {
                        text = "✕"
                        textSize = 18f
                        gravity = Gravity.CENTER
                        setTextColor(attrColor(android.R.attr.textColorSecondary))
                        setPadding(dp(14), dp(4), dp(14), dp(4))
                        isClickable = true
                        isFocusable = true
                        background = selectableBackground()
                        contentDescription = "Delete $title"
                        setOnClickListener { onDelete() }
                    })
                }
            }
        }

        for (m in TranscriberClient.CLOUD_MODELS) {
            list.addView(row(m.title, m.price, m.id == current, m.details) {
                prefs().edit().putString("cloud_model", m.id).apply()
                dialog?.dismiss(); refresh()
            })
        }
        // A custom model chosen before saved models existed still shows up, and is kept.
        if (isCustom && current !in savedCustomModels()) setSavedCustomModels(savedCustomModels() + current)
        for (id in savedCustomModels().filter { s -> TranscriberClient.CLOUD_MODELS.none { it.id == s } }) {
            list.addView(row(id, "Custom model", id == current, null, onDelete = {
                setSavedCustomModels(savedCustomModels() - id)
                if (id == current) prefs().edit().putString("cloud_model", TranscriberClient.DEFAULT_MODEL).apply()
                dialog?.dismiss(); refresh(); pickCloudModel()
            }) {
                prefs().edit().putString("cloud_model", id).apply()
                dialog?.dismiss(); refresh()
            })
        }
        list.addView(row("Add custom model", "Paste any OpenRouter model ID", false,
            "Use any speech-to-text model on OpenRouter. Copy its ID from the model's OpenRouter page " +
            "(for example, deepgram/nova-3) and paste it here.\n\n" +
            "Verbatim mode sends a keep-everything setting to Microsoft, ElevenLabs, and AssemblyAI models. " +
            "Other models use their own default. In testing, Mistral and AssemblyAI kept \"um\" and \"uh\" anyway, " +
            "while Deepgram removed them no matter what.") {
            dialog?.dismiss(); promptCustomModel()
        })

        dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Cloud model")
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Language picker: Auto plus the languages the chosen model accepts. */
    private fun pickLanguage() {
        val model = prefs().getString("cloud_model", TranscriberClient.DEFAULT_MODEL) ?: TranscriberClient.DEFAULT_MODEL
        val keys = listOf("auto") + TranscriberClient.languagesFor(model)
        val names = keys.map { if (it == "auto") "Auto" else TranscriberClient.LANGUAGES[it] }.toTypedArray()
        val current = keys.indexOf(prefs().getString("language", "auto")).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("Language")
            .setSingleChoiceItems(names, current) { d, which ->
                prefs().edit().putString("language", keys[which]).apply()
                d.dismiss(); refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Custom model IDs the user added, in the order they were added. */
    private fun savedCustomModels(): List<String> =
        (prefs().getString("custom_models", "") ?: "").split('\n').filter { it.isNotBlank() }

    private fun setSavedCustomModels(ids: List<String>) =
        prefs().edit().putString("custom_models", ids.distinct().joinToString("\n")).apply()

    private fun promptCustomModel() {
        val input = EditText(this).apply {
            hint = "provider/model-name"
            isSingleLine = true
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Add custom model")
            .setMessage("Paste the model's ID from OpenRouter, for example deepgram/nova-3.")
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Save") { _, _ ->
                // Accept a pasted page link too: keep only the "provider/model" part.
                val id = input.text.toString().trim()
                    .removePrefix("https://").removePrefix("openrouter.ai/").trim('/')
                if (id.contains('/')) {
                    if (TranscriberClient.CLOUD_MODELS.none { it.id == id }) setSavedCustomModels(savedCustomModels() + id)
                    prefs().edit().putString("cloud_model", id).apply()
                    refresh()
                } else {
                    Toast.makeText(this, "That doesn't look like a model ID. It should look like provider/model-name.", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Browse models") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://openrouter.ai/collections/speech-to-text-models")))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectableBackground(): android.graphics.drawable.Drawable? {
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return ContextCompat.getDrawable(this, tv.resourceId)
    }

    private fun promptPostProcessing() {
        val input = EditText(this).apply {
            hint = "Prompt"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setText(currentPrompt())
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Edit current prompt")
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                val finalPrompt = if (text.isBlank()) PostProcessor.DEFAULT_PROMPT else text
                prefs().edit()
                    .putString("custom_post_processing_prompt", finalPrompt)
                    .putString("post_processing_prompt", finalPrompt)
                    .apply()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- UI Helpers ---

    private fun settingsRow(title: String, subtitle: String, widget: View? = null, onClick: (() -> Unit)? = null): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
            isClickable = onClick != null
            isFocusable = onClick != null
            if (onClick != null) {
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { onClick() }
            }
        }

        val textContainer = vertical(0).apply {
            layoutParams = LinearLayout.LayoutParams(0, LP_WRAP, 1f)
        }
        
        textContainer.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(attrColor(android.R.attr.textColorPrimary))
        })
        
        textContainer.addView(TextView(this).apply {
            tag = "subtitle"
            text = subtitle
            textSize = 14f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(2), 0, 0)
        })

        row.addView(textContainer)
        if (widget != null) row.addView(widget)

        return row
    }

    private fun sectionHeader(title: String) = TextView(this).apply {
        text = title
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(attrColor(com.google.android.material.R.attr.colorPrimary)) // Neutral Android-like blue
        setPadding(dp(24), dp(24), dp(24), dp(8))
    }

    private fun vertical(padH: Int, padV: Int = padH) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padH, padV, padH, padV)
    }

    private fun currentPrompt() = prefs().getString("post_processing_prompt", PostProcessor.DEFAULT_PROMPT) ?: PostProcessor.DEFAULT_PROMPT
    private fun customPrompt() = prefs().getString("custom_post_processing_prompt", PostProcessor.DEFAULT_PROMPT) ?: PostProcessor.DEFAULT_PROMPT

    private fun customPromptSummary(): String {
        val prompt = customPrompt()
        return if (prompt == PostProcessor.DEFAULT_PROMPT) "Your edited prompt"
        else prompt.replace("\n", " ")
    }

    private data class PromptPreset(val key: String, val title: String, val subtitle: String, val prompt: String)

    private fun promptPresets() = listOf(
        PromptPreset(
            key = "dev",
            title = "Dev cleanup",
            subtitle = "Best for coding, CLI, and project names",
            prompt = PostProcessor.DEV_PROMPT
        ),
        PromptPreset(
            key = "simple",
            title = "Simple cleanup",
            subtitle = "Grammar, punctuation, and light cleanup",
            prompt = PostProcessor.SIMPLE_PROMPT
        ),
        PromptPreset(
            key = "custom",
            title = "Custom",
            subtitle = customPromptSummary(),
            prompt = customPrompt()
        )
    )

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun attrColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }
    private fun prefs() = getSharedPreferences("phonewhisper", MODE_PRIVATE)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val LP_MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val LP_WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    }
}
