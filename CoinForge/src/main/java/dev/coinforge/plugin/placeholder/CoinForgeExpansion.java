package dev.coinforge.plugin.placeholder;

import dev.coinforge.plugin.CoinForge;
import dev.coinforge.plugin.currency.Currency;
import dev.coinforge.plugin.currency.CurrencyManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Provides:
 *   %coinforge_balance_<currency>%    -> raw numeric balance
 *   %coinforge_formatted_<currency>%  -> balance with symbol/decimals applied
 */
public class CoinForgeExpansion extends PlaceholderExpansion {

    private final CoinForge plugin;
    private final CurrencyManager currencyManager;

    public CoinForgeExpansion(CoinForge plugin, CurrencyManager currencyManager) {
        this.plugin = plugin;
        this.currencyManager = currencyManager;
    }

    @Override
    public String getIdentifier() {
        return "coinforge";
    }

    @Override
    public String getAuthor() {
        return "CoinForge";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";

        if (params.startsWith("balance_")) {
            String currencyId = params.substring("balance_".length());
            if (!currencyManager.exists(currencyId)) return "0";
            double bal = currencyManager.getBalance(player, currencyId);
            return String.valueOf(bal);
        }

        if (params.startsWith("formatted_")) {
            String currencyId = params.substring("formatted_".length());
            Currency currency = currencyManager.get(currencyId);
            if (currency == null) return "";
            double bal = currencyManager.getBalance(player, currencyId);
            return currency.format(bal);
        }

        return null;
    }
}
