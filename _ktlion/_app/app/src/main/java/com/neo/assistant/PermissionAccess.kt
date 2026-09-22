package com.neo.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

class PermissionAccess(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences("neo_permissions", Activity.MODE_PRIVATE)

    fun allowed(permission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        } else {
            activity.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun settings() {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
    }

    fun request(permission: String, code: Int, explanation: String, onCancel: () -> Unit = {}) {
        if (allowed(permission)) { settings(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (prefs.getBoolean(permission, false) && !activity.shouldShowRequestPermissionRationale(permission)) {
                AlertDialog.Builder(activity).setTitle("Manage access in Android")
                    .setMessage("$explanation\n\nAndroid may no longer show the permission prompt. Open Permissions to allow or deny access.")
                    .setNegativeButton("Not now") { _, _ -> onCancel() }.setOnCancelListener { onCancel() }
                    .setPositiveButton("Open settings") { _, _ -> settings() }.show()
                return
            }
            AlertDialog.Builder(activity).setTitle("Choose what Neo can access")
                .setMessage(explanation).setNegativeButton("Not now") { _, _ -> onCancel() }.setOnCancelListener { onCancel() }
                .setPositiveButton("Continue") { _, _ ->
                    prefs.edit().putBoolean(permission, true).apply()
                    activity.requestPermissions(arrayOf(permission), code)
                }.show()
        } else {
            settings()
        }
    }

    fun requestMultiple(permissions: Array<String>, code: Int, explanation: String, onCancel: () -> Unit = {}) {
        val ungranted = permissions.filter { !allowed(it) }.toTypedArray()
        if (ungranted.isEmpty()) { settings(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ungranted.any { prefs.getBoolean(it, false) && !activity.shouldShowRequestPermissionRationale(it) }) {
                AlertDialog.Builder(activity).setTitle("Manage access in Android")
                    .setMessage("$explanation\n\nA permission was previously denied. Android may require you to change it in app Settings.")
                    .setNegativeButton("Not now") { _, _ -> onCancel() }.setOnCancelListener { onCancel() }
                    .setPositiveButton("Open settings") { _, _ -> settings() }.show()
                return
            }
            AlertDialog.Builder(activity).setTitle("Choose what Neo can access")
                .setMessage(explanation).setNegativeButton("Not now") { _, _ -> onCancel() }.setOnCancelListener { onCancel() }
                .setPositiveButton("Continue") { _, _ ->
                    ungranted.forEach { prefs.edit().putBoolean(it, true).apply() }
                    activity.requestPermissions(ungranted, code)
                }.show()
        } else {
            settings()
        }
    }
}
