package dev.coinforge.plugin.currency;

import dev.coinforge.plugin.storage.DataStorage;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads currency definitions from config.yml and owns every balance
 * operation. Both the vault coin and custom coins are stored and managed
 * identically via DataStorage - CoinForge is the single source of truth
 * for all of them. Whether the vault coin is also exposed to other plugins
 * through Vault is a separate concern (see CoinForgeEconomy).
 */
public class CurrencyManager {

    private final DataStorage dataStorage;
    private final Map<String, Currency> currencies = new LinkedHashMap<>();

    private boolean vaultCurrencyEnabled = false;
    private boolean registerWithVault = false;
    private String vaultPriority = "HIGH";
    private String vaultCurrencyId = null;

    public CurrencyManager(DataStorage dataStorage) {
        this.dataStorage = dataStorage;
    }

    public void load(FileConfiguration config) {
        currencies.clear();
        vaultCurrencyEnabled = false;
        registerWithVault = false;
        vaultCurrencyId = null;

        ConfigurationSection vaultSection = config.getConfigurationSection("vault-currency");
        if (vaultSection != null && vaultSection.getBoolean("enabled", true)) {
            vaultCurrencyEnabled = true;
            registerWithVault = vaultSection.getBoolean("register-with-vault", true);
            vaultPriority = vaultSection.getString("priority", "HIGH");
            vaultCurrencyId = vaultSection.getString("id", "coins").toLowerCase();

            Currency vaultCurrency = new Currency(
                    vaultCurrencyId,
                    Currency.Type.VAULT,
                    vaultSection.getString("display-name", "Coins"),
                    vaultSection.getString("symbol", "$"),
                    vaultSection.getInt("decimal-places", 2),
                    vaultSection.getDouble("starting-balance", 0.0),
                    parseMaterial(vaultSection.getString("icon", "GOLD_INGOT"), Material.GOLD_INGOT)
            );
            currencies.put(vaultCurrencyId, vaultCurrency);
        }

        ConfigurationSection customSection = config.getConfigurationSection("custom-currencies");
        if (customSection != null) {
            for (String id : customSection.getKeys(false)) {
                ConfigurationSection c = customSection.getConfigurationSection(id);
                if (c == null) continue;
                Currency currency = new Currency(
                        id,
                        Currency.Type.CUSTOM,
                        c.getString("display-name", id),
                        c.getString("symbol", ""),
                        c.getInt("decimal-places", 0),
                        c.getDouble("starting-balance", 0.0),
                        parseMaterial(c.getString("icon", "SUNFLOWER"), Material.SUNFLOWER)
                );
                currencies.put(currency.getId(), currency);
            }
        }

        dataStorage.load(currencies.keySet());
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public Currency get(String id) {
        return id == null ? null : currencies.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return id != null && currencies.containsKey(id.toLowerCase());
    }

    public Map<String, Currency> getAll() {
        return currencies;
    }

    public boolean isVaultCurrencyEnabled() {
        return vaultCurrencyEnabled;
    }

    public boolean shouldRegisterWithVault() {
        return registerWithVault;
    }

    public String getVaultPriority() {
        return vaultPriority;
    }

    /** Returns null if the vault coin is disabled. */
    public String getVaultCurrencyId() {
        return vaultCurrencyId;
    }

    /** Called on join to give new players their starting balance for every coin. */
    public void ensureAccount(OfflinePlayer player) {
        for (Currency currency : currencies.values()) {
            if (!dataStorage.hasAccount(currency.getId(), player.getUniqueId())) {
                dataStorage.setBalance(currency.getId(), player.getUniqueId(), currency.getStartingBalance());
            }
        }
    }

    public double getBalance(OfflinePlayer player, String currencyId) {
        Currency currency = get(currencyId);
        if (currency == null) return 0.0;
        return dataStorage.getBalance(currency.getId(), player.getUniqueId(), currency.getStartingBalance());
    }

    public boolean has(OfflinePlayer player, String currencyId, double amount) {
        return getBalance(player, currencyId) >= amount;
    }

    public boolean deposit(OfflinePlayer player, String currencyId, double amount) {
        Currency currency = get(currencyId);
        if (currency == null || amount < 0) return false;
        double newBalance = getBalance(player, currencyId) + amount;
        dataStorage.setBalance(currency.getId(), player.getUniqueId(), newBalance);
        return true;
    }

    public boolean withdraw(OfflinePlayer player, String currencyId, double amount) {
        Currency currency = get(currencyId);
        if (currency == null || amount < 0) return false;
        if (!has(player, currencyId, amount)) return false;
        double newBalance = getBalance(player, currencyId) - amount;
        dataStorage.setBalance(currency.getId(), player.getUniqueId(), newBalance);
        return true;
    }

    public boolean setBalance(OfflinePlayer player, String currencyId, double amount) {
        Currency currency = get(currencyId);
        if (currency == null || amount < 0) return false;
        dataStorage.setBalance(currency.getId(), player.getUniqueId(), amount);
        return true;
    }

    /** Resets a player's balance for one coin back to its configured starting balance. */
    public boolean resetBalance(OfflinePlayer player, String currencyId) {
        Currency currency = get(currencyId);
        if (currency == null) return false;
        dataStorage.setBalance(currency.getId(), player.getUniqueId(), currency.getStartingBalance());
        return true;
    }
}
