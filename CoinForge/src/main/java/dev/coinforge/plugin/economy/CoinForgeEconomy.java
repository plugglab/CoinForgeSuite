package dev.coinforge.plugin.economy;

import dev.coinforge.plugin.currency.Currency;
import dev.coinforge.plugin.currency.CurrencyManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;

/**
 * A full Vault Economy implementation backed by CoinForge's own storage for
 * the vault-currency. When registered with a high enough ServicePriority,
 * this takes over as the server's economy from EssentialsX, CMI, or any
 * other economy plugin - the same way those plugins override each other.
 * Banking is intentionally unsupported.
 */
public class CoinForgeEconomy implements Economy {

    private final CurrencyManager currencyManager;

    public CoinForgeEconomy(CurrencyManager currencyManager) {
        this.currencyManager = currencyManager;
    }

    private Currency currency() {
        return currencyManager.get(currencyManager.getVaultCurrencyId());
    }

    @Override
    public boolean isEnabled() {
        return currencyManager.isVaultCurrencyEnabled();
    }

    @Override
    public String getName() {
        return "CoinForge";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        Currency c = currency();
        return c == null ? 2 : c.getDecimalPlaces();
    }

    @Override
    public String format(double amount) {
        Currency c = currency();
        return c == null ? String.valueOf(amount) : c.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        Currency c = currency();
        return c == null ? "Coins" : c.getDisplayName();
    }

    @Override
    public String currencyNameSingular() {
        Currency c = currency();
        return c == null ? "Coin" : c.getDisplayName();
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer player(String name) {
        return Bukkit.getOfflinePlayer(name);
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName) {
        return hasAccount(player(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true; // accounts are created lazily on first balance check
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    @Deprecated
    public double getBalance(String playerName) {
        return getBalance(player(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return currencyManager.getBalance(player, currencyManager.getVaultCurrencyId());
    }

    @Override
    @Deprecated
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    @Deprecated
    public boolean has(String playerName, double amount) {
        return has(player(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return currencyManager.has(player, currencyManager.getVaultCurrencyId(), amount);
    }

    @Override
    @Deprecated
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(player(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        String id = currencyManager.getVaultCurrencyId();
        if (!currencyManager.has(player, id, amount)) {
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Insufficient funds.");
        }
        currencyManager.withdraw(player, id, amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(player(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        String id = currencyManager.getVaultCurrencyId();
        currencyManager.deposit(player, id, amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, "");
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    private EconomyResponse notSupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported by CoinForge.");
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notSupported();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notSupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notSupported();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(player(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        currencyManager.ensureAccount(player);
        return true;
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }
}
