package com.jarvismini.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import com.jarvismini.core.WorkModeManager
import com.jarvismini.core.workmode.WorkModeService

class WorkModeTileService : TileService() {

    override fun onClick() {
        super.onClick()

        val isNowOn = WorkModeManager.toggle(this)

        if (isNowOn) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, WorkModeService::class.java)
            )
        } else {
            stopService(Intent(this, WorkModeService::class.java))
        }

        qsTile.state =
            if (JarvisState.currentMode == JarvisMode.WORK)
                Tile.STATE_ACTIVE
            else
                Tile.STATE_INACTIVE

        qsTile.updateTile()
    }
}
