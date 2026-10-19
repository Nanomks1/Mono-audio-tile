package com.example.monotile

import android.content.pm.PackageManager
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import rikka.shizuku.Shizuku

class MonoTileService : TileService() {

    private val REQUEST_CODE_SHIZUKU = 1001

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU && grantResult == PackageManager.PERMISSION_GRANTED) {
            grantSelfPermissionsViaShizuku()
            toggleMonoAudio()
        } else {
            Toast.makeText(this, "لم يتم منح الصلاحية", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        toggleMonoAudio()
    }

    private fun toggleMonoAudio() {
        val currentMono = isMonoEnabled()
        val targetValue = if (currentMono) 0 else 1

        // المحاولة 1: التغيير المباشر عبر النظام (يعمل فوراً إذا منحت التطبيق الصلاحية بأي تطبيق خارجي)
        try {
            val success = Settings.System.putInt(contentResolver, "master_mono", targetValue)
            if (success) {
                updateTileState()
                return
            }
        } catch (ignored: Throwable) {
            // إذا لم يكن يملك الصلاحية المباشرة، ينتقل للمحاولة الثانية تلقائياً
        }

        // المحاولة 2: استخدام بروتوكول Shizuku/Sui لمنح الصلاحية وتغيير الإعداد
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    grantSelfPermissionsViaShizuku()
                    execShizukuCommand("settings put system master_mono $targetValue")
                    updateTileState()
                } else {
                    Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
                }
            } else {
                Toast.makeText(
                    this,
                    "امنح التطبيق صلاحية WRITE_SECURE_SETTINGS أو شغّل Shizuku",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // يمنح التطبيق صلاحية دائمة من أول ضغطة حتى يعمل مستقبلاً بدون الحاجة لتشغيل Shizuku في الخلفية!
    private fun grantSelfPermissionsViaShizuku() {
        try {
            execShizukuCommand("pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
            execShizukuCommand("appops set $packageName WRITE_SETTINGS allow")
        } catch (ignored: Throwable) {}
    }

    @Suppress("DEPRECATION")
    private fun execShizukuCommand(command: String) {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        process.waitFor()
    }

    private fun isMonoEnabled(): Boolean {
        return try {
            Settings.System.getInt(contentResolver, "master_mono", 0) == 1
        } catch (e: Throwable) {
            false
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val enabled = isMonoEnabled()

        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Mono Audio"
        tile.subtitle = if (enabled) "مُفعّل" else "مُعطّل"
        tile.updateTile()
    }
}
