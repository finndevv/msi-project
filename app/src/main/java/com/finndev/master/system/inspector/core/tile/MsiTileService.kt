package com.finndev.master.system.inspector.core.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.finndev.master.system.inspector.MainActivity

/**
 * Quick Settings notification tile (module 63).
 * Tapping the tile opens the MSI Alpine terminal instantly.
 */
class MsiTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = if (com.finndev.master.system.inspector.core.TerminalCenter.state.value ==
                com.finndev.master.system.inspector.core.TerminalCenter.State.RUNNING
            ) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle = "MSI v" + com.finndev.master.system.inspector.BuildConfig.VERSION_NAME
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        qsTile?.let {
            it.state = Tile.STATE_ACTIVE
            it.updateTile()
        }
        val intent = android.content.Intent(this, MainActivity::class.java).apply {
            putExtra("open", "terminal")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(intent)
    }
}
