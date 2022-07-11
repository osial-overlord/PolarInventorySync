package org.polar.polarinventorysync.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.polar.polarinventorysync.PolarInventorySync;
import org.polar.polarinventorysync.data.DataHandler;
import org.polar.polarinventorysync.util.NBTEditor;

public class ConnectionListener implements Listener {

    private PolarInventorySync plugin;
    private boolean once = false;

    public ConnectionListener(PolarInventorySync plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        DataHandler dataHandler = plugin.getDataHandler();
        dataHandler.loadInventoryContents(e.getPlayer());

        if (!once) {
            e.getPlayer().getInventory().forEach(item -> {
                if (item != null) {
                    ItemStack i = NBTEditor.set(item.clone(), true, "polar_async");
                    e.getPlayer().getInventory().setItem(e.getPlayer().getInventory().first(item), i);
                }
            });

            dataHandler.savePlayerInventory(e.getPlayer());
            once = true;
        }
    }

    @EventHandler
    public void onQuit(PlayerJoinEvent e) {
        DataHandler dataHandler = plugin.getDataHandler();
        dataHandler.savePlayerInventory(e.getPlayer());
    }

}
