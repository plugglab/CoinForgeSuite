package dev.coinforge.plugin.listeners;

import dev.coinforge.plugin.currency.CurrencyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final CurrencyManager currencyManager;

    public PlayerJoinListener(CurrencyManager currencyManager) {
        this.currencyManager = currencyManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        currencyManager.ensureAccount(event.getPlayer());
    }
}
