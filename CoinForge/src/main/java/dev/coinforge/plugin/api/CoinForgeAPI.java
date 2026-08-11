package dev.coinforge.plugin.api;

import dev.coinforge.plugin.currency.Currency;
import dev.coinforge.plugin.currency.CurrencyManager;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.Set;

/**
 * The public integration point for other plugins to read/modify CoinForge
 * balances. Other plugins should soft- or hard-depend on "CoinForge" in
 * their plugin.yml and only call CoinForgeAPI.get() after confirming
 * CoinForge is present - it returns null if CoinForge isn't loaded/enabled.
 *
 * Example:
 *   CoinForgeAPI api = CoinForgeAPI.get();
 *   if (api != null && api.has(player, "coins", 100)) {
 *       api.withdraw(player, "coins", 100);
 *   }
 */
public final class CoinForgeAPI {

    private static CoinForgeAPI instance;

    private final CurrencyManager currencyManager;

    private CoinForgeAPI(CurrencyManager currencyManager) {
        this.currencyManager = currencyManager;
    }

    public static void init(CurrencyManager currencyManager) {
        instance = new CoinForgeAPI(currencyManager);
    }

    public static void shutdown() {
        instance = null;
    }

    /** Returns null if CoinForge isn't installed or hasn't finished enabling yet. */
    public static CoinForgeAPI get() {
        return instance;
    }

    public boolean currencyExists(String currencyId) {
        return currencyManager.exists(currencyId);
    }

    /** All configured coin IDs, including the vault coin if it's enabled. */
    public Set<String> getCurrencyIds() {
        return Collections.unmodifiableSet(currencyManager.getAll().keySet());
    }

    public String getDisplayName(String currencyId) {
        Currency c = currencyManager.get(currencyId);
        return c == null ? null : c.getDisplayName();
    }

    public String getSymbol(String currencyId) {
        Currency c = currencyManager.get(currencyId);
        return c == null ? null : c.getSymbol();
    }

    /** Formats an amount with the coin's symbol and decimal places, e.g. "$1,250.00". */
    public String format(String currencyId, double amount) {
        Currency c = currencyManager.get(currencyId);
        return c == null ? String.valueOf(amount) : c.format(amount);
    }

    public double getBalance(OfflinePlayer player, String currencyId) {
        return currencyManager.getBalance(player, currencyId);
    }

    public boolean has(OfflinePlayer player, String currencyId, double amount) {
        return currencyManager.has(player, currencyId, amount);
    }

    public boolean deposit(OfflinePlayer player, String currencyId, double amount) {
        return currencyManager.deposit(player, currencyId, amount);
    }

    public boolean withdraw(OfflinePlayer player, String currencyId, double amount) {
        return currencyManager.withdraw(player, currencyId, amount);
    }
}
