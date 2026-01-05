package com.jarvismini.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.jarvismini.core.WorkModeManager

class WorkModeTileService : TileService() {

    override fun onClick() {
        super.onClick()
        WorkModeManager.activate(this)
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
    }
}
