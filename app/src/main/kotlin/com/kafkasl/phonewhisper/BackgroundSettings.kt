package com.kafkasl.phonewhisper

import android.app.Activity
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * Many phone makers stop apps in the background on top of Android's own battery saving, and
 * "clear all" in the recent apps (Xiaomi's OneKeyClean and the like) kills every process that
 * isn't exempt. Android restarts a killed accessibility service only if the maker's system lets
 * the app start again; otherwise the service stays in Android's "crashed" list, and the
 * accessibility settings show an error until it is switched off and on.
 *
 * This lists, per maker, the system settings that prevent that (after dontkillmyapp.com) and
 * opens them. The makers' screens are not public API: each step tries known activities from
 * several system versions and falls back to the app's info page.
 */
object BackgroundSettings {
    private const val TAG = "PhoneWhisper"

    enum class Vendor(val label: String) {
        XIAOMI("Xiaomi, Redmi and POCO (HyperOS, MIUI)"),
        SAMSUNG("Samsung (One UI)"),
        HUAWEI("Huawei (EMUI)"),
        HONOR("Honor (MagicOS)"),
        OPPO("OPPO, realme and OnePlus (ColorOS)"),
        VIVO("vivo and iQOO (OriginOS, Funtouch OS)"),
        ASUS("ASUS"),
        MEIZU("Meizu (Flyme)"),
        TRANSSION("Tecno, Infinix and itel"),
        OTHER("this phone"),
    }

    enum class Status { DONE, TODO, UNKNOWN }

    class Step(
        val title: String,
        /** Where to tap once the screen is open. */
        val howTo: String,
        /** The maker's screens, most recent system version first; app info if none opens. */
        val components: List<ComponentName> = emptyList(),
        val extras: Map<String, String> = emptyMap(),
        /** Android's own battery optimization list instead of a maker's screen. */
        val batteryOptimization: Boolean = false,
        val status: (Context) -> Status = { Status.UNKNOWN },
    )

    fun vendor(manufacturer: String = Build.MANUFACTURER.orEmpty(), brand: String = Build.BRAND.orEmpty()): Vendor {
        val names = listOf(manufacturer, brand).map { it.lowercase() }
        fun any(vararg keys: String) = names.any { name -> keys.any { name.contains(it) } }
        return when {
            any("xiaomi", "redmi", "poco") -> Vendor.XIAOMI
            any("samsung") -> Vendor.SAMSUNG
            any("honor") -> Vendor.HONOR
            any("huawei") -> Vendor.HUAWEI
            any("oppo", "realme", "oneplus") -> Vendor.OPPO
            any("vivo", "iqoo") -> Vendor.VIVO
            any("asus") -> Vendor.ASUS
            any("meizu") -> Vendor.MEIZU
            any("tecno", "infinix", "itel") -> Vendor.TRANSSION
            else -> Vendor.OTHER
        }
    }

    /** Whether the maker lets you lock an app in the recent apps, which exempts it from "clear all". */
    fun hasRecentsLock(vendor: Vendor) = vendor in setOf(
        Vendor.XIAOMI, Vendor.HUAWEI, Vendor.HONOR, Vendor.OPPO, Vendor.VIVO, Vendor.MEIZU, Vendor.TRANSSION,
    )

    fun steps(vendor: Vendor, packageName: String): List<Step> = buildList {
        when (vendor) {
            Vendor.XIAOMI -> {
                add(Step(
                    "Allow Autostart",
                    "Turn on Autostart for Word for Word. Without it the system won't restart the service after clearing recent apps",
                    listOf(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                    status = ::xiaomiAutostart,
                ))
                add(Step(
                    "Battery saver: No restrictions",
                    "Tap Battery saver and choose \"No restrictions\"",
                    listOf(
                        // Xiaomi's app info (HyperOS), then the older MIUI battery saver list.
                        ComponentName("com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity"),
                        ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                    ),
                    extras = mapOf("package_name" to packageName, "package_label" to "Word for Word"),
                ))
            }
            Vendor.SAMSUNG -> {
                add(batteryStep("Battery: Unrestricted", "Tap Battery and choose \"Unrestricted\""))
                add(Step(
                    "Never sleeping apps",
                    "Background usage limits → Never sleeping apps → + → Word for Word. Also make sure it isn't under Sleeping or Deep sleeping apps",
                    listOf(
                        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    ),
                ))
            }
            Vendor.HUAWEI, Vendor.HONOR -> {
                val pkg = if (vendor == Vendor.HONOR) "com.hihonor.systemmanager" else "com.huawei.systemmanager"
                add(Step(
                    "App launch: manage manually",
                    "Turn off \"Manage automatically\" for Word for Word, then keep Auto-launch, Secondary launch and Run in background on",
                    listOf(
                        ComponentName(pkg, "$pkg.startupmgr.ui.StartupNormalAppListActivity"),
                        ComponentName(pkg, "$pkg.appcontrol.activity.StartupAppControlActivity"),
                        ComponentName(pkg, "$pkg.optimize.process.ProtectActivity"),
                    ),
                ))
                add(batteryStep("Battery optimization: Don't allow", "Choose \"All apps\", find Word for Word and choose \"Don't allow\""))
            }
            Vendor.OPPO -> {
                add(Step(
                    "Allow auto launch",
                    "Turn on auto launch (Auto startup) for Word for Word",
                    listOf(
                        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                        ComponentName("com.coloros.safecenter", "com.coloros.privacypermissionsentry.PermissionTopActivity"),
                        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                        ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                    ),
                ))
                add(batteryStep("Battery: allow background activity", "Tap Battery usage and turn on \"Allow background activity\""))
            }
            Vendor.VIVO -> {
                add(Step(
                    "Allow background and auto start",
                    "Allow auto start and high background power consumption for Word for Word",
                    listOf(
                        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                        ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
                    ),
                ))
                add(batteryStep("Battery: Unrestricted", "Tap Battery and choose \"Unrestricted\" or allow high background usage"))
            }
            Vendor.ASUS -> {
                add(Step(
                    "Allow auto-start",
                    "Allow auto-start for Word for Word",
                    listOf(
                        ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                        ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"),
                    ),
                ))
                add(batteryStep("Battery: Unrestricted", "Tap Battery and choose \"Unrestricted\""))
            }
            Vendor.MEIZU -> {
                add(Step(
                    "Allow running in the background",
                    "Allow Word for Word to run in the background",
                    listOf(ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")),
                ))
                add(batteryStep("Battery: Unrestricted", "Tap Battery and choose \"Unrestricted\""))
            }
            Vendor.TRANSSION -> {
                add(Step(
                    "Allow auto start",
                    "Turn on auto start for Word for Word",
                    listOf(ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")),
                ))
                add(batteryStep("Battery: Unrestricted", "Tap Battery and choose \"Unrestricted\""))
            }
            Vendor.OTHER -> add(batteryStep("Battery: Unrestricted", "Tap Battery (App battery usage) and choose \"Unrestricted\""))
        }
    }

    /** Android's own battery optimization; the maker's "unrestricted" maps to it on most phones. */
    private fun batteryStep(title: String, howTo: String) = Step(title, howTo, batteryOptimization = true) {
        // Android 5 has no battery optimization, so there is nothing to turn off.
        if (android.os.Build.VERSION.SDK_INT < 23) Status.DONE
        else if ((it.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(it.packageName)) Status.DONE
        else Status.TODO
    }

    /** Xiaomi stores Autostart as its own app op (10008); reading it is not public API, so it may fail. */
    private fun xiaomiAutostart(context: Context): Status = try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val check = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
        )
        if (check.invoke(ops, 10008, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED) Status.DONE
        else Status.TODO
    } catch (e: Exception) {
        Log.w(TAG, "Can't read the Autostart setting: $e")
        Status.UNKNOWN
    }

    /** Open the step's screen; app info if the maker's screen doesn't exist on this version. */
    fun open(activity: Activity, step: Step) {
        val candidates = step.components.map { component ->
            Intent().setComponent(component).apply { step.extras.forEach { (k, v) -> putExtra(k, v) } }
        } + listOfNotNull(
            // On Android 12+ app info has the Battery entry with "Unrestricted".
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")),
            if (step.batteryOptimization && android.os.Build.VERSION.SDK_INT >= 23) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) else null,
        )
        for (intent in candidates) {
            try {
                activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
                // Some makers' screens exist but aren't exported.
            }
        }
    }
}
