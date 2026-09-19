package com.neo.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

class PermissionAccess(private val activity: Activity) {
    private val prefs = activity.getSharedPreferences("neo_permissions", Activity.MODE_PRIVATE)
    fun allowed(permission: String) = activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    fun settings() {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
    }
    fun request(permission: String, code: Int, explanation: String, onCancel: () -> Unit = {}) {
        if (allowed(permission)) { settings(); return }
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
    }
}
