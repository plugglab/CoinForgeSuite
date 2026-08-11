package dev.coinforge.plugin.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class CoinsGuiListener implements Listener {

    private final CoinsGuiManager guiManager;

    public CoinsGuiListener(CoinsGuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CoinsInventoryHolder)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        event.setCancelled(true);

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(clicked.getHolder() instanceof CoinsInventoryHolder)) {
            return; // click landed in the player's own inventory - ignore
        }

        Player player = (Player) event.getWhoClicked();
        CoinsGuiSession session = guiManager.getSession(player);
        if (session == null) return;

        int slot = event.getSlot();

        if (session.getType() == CoinsGuiSession.Type.WALLET) {
            String currencyId = session.getCurrencySlots().get(slot);
            if (currencyId != null) {
                guiManager.openLeaderboard(player, currencyId, 0);
                return;
            }
        }

        String nav = session.getNavSlots().get(slot);
        if (nav == null) return;

        switch (nav) {
            case "back":
                guiManager.openWallet(player);
                break;
            case "refresh":
                guiManager.refresh(player, session);
                break;
            case "close":
                player.closeInventory();
                break;
            default:
                break;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CoinsInventoryHolder)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CoinsInventoryHolder)) return;
        if (event.getPlayer() instanceof Player) {
            guiManager.clearSession((Player) event.getPlayer());
        }
    }
}
