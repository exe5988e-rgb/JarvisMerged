package com.jarvismini.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import com.jarvismini.core.WorkModeManager

class WorkModeTileService : TileService() {

    override fun onClick() {
        super.onClick()

        WorkModeManager.toggle(this)

        qsTile.state =
            if (JarvisState.currentMode == JarvisMode.WORK)
                Tile.STATE_ACTIVE
            else
                Tile.STATE_INACTIVE

        qsTile.updateTile()
    }
}
