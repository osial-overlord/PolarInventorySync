package org.polar.polarinventorysync;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.polar.polarinventorysync.data.DataHandler;
import org.polar.polarinventorysync.listener.ConnectionListener;

public final class PolarInventorySync extends JavaPlugin {

    @Getter private DataHandler dataHandler;
    private ConnectionListener connectionListener;

    @Override
    public void onEnable() {
        this.dataHandler = new DataHandler(this);
        this.connectionListener = new ConnectionListener(this);
        this.getServer().getPluginManager().registerEvents(this.connectionListener, this);
    }

    @Override
    public void onDisable() {

    }

}
