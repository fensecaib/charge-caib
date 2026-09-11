package caib.charge.system

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import caib.charge.MainActivity
import caib.charge.R

/** Opens the existing launcher task; never changes the editor or writes ledger data. */
class LaunchAppTileService : TileService() {
    override fun onTileAdded() {
        super.onTileAdded()
        // Initialize the cached action state without binding on every panel expansion.
        requestListeningState(this, ComponentName(this, LaunchAppTileService::class.java))
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            contentDescription = getString(R.string.quick_settings_open_app)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openApp() } else openApp()
    }

    private fun openApp() {
        // Match a launcher tap, including bringing an existing task back unchanged.
        val intent = Intent.makeMainActivity(ComponentName(this, MainActivity::class.java))
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            openAppBeforeAndroid14(intent)
        }
    }

    // API 31–33 have no PendingIntent overload. The caller excludes API 34+.
    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAppBeforeAndroid14(intent: Intent) {
        startActivityAndCollapse(intent)
    }
}
