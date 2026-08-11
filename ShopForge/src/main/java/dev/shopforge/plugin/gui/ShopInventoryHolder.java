package dev.shopforge.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Used purely to identify ShopForge's own GUI inventories in event handlers
 * (event.getInventory().getHolder() instanceof ShopInventoryHolder), which is
 * stable across every Bukkit/Spigot/Paper version - unlike title matching.
 */
public class ShopInventoryHolder implements InventoryHolder {
    @Override
    public Inventory getInventory() {
        return null;
    }
}
