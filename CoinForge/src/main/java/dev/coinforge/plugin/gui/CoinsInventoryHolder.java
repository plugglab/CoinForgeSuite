package dev.coinforge.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Used purely to identify CoinForge's own GUI inventories in event handlers,
 * which is stable across every Bukkit/Spigot/Paper version - unlike title
 * matching.
 */
public class CoinsInventoryHolder implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}
