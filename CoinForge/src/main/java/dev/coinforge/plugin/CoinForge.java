package dev.coinforge.plugin;

import dev.coinforge.plugin.api.CoinForgeAPI;
import dev.coinforge.plugin.commands.CoinsCommand;
import dev.coinforge.plugin.currency.CurrencyManager;
import dev.coinforge.plugin.currency.ExchangeManager;
import dev.coinforge.plugin.currency.InterestManager;
import dev.coinforge.plugin.economy.CoinForgeEconomy;
import dev.coinforge.plugin.gui.CoinsGuiListener;
import dev.coinforge.plugin.gui.CoinsGuiManager;
import dev.coinforge.plugin.listeners.PlayerJoinListener;
import dev.coinforge.plugin.placeholder.CoinForgeExpansion;
import dev.coinforge.plugin.storage.DataStorage;
import dev.coinforge.plugin.storage.TransactionLogger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class CoinForge extends JavaPlugin {

    private DataStorage dataStorage;
    private CurrencyManager currencyManager;
    private ExchangeManager exchangeManager;
    private InterestManager interestManager;
    private TransactionLogger transactionLogger;
    private CoinForgeEconomy coinForgeEconomy;
    private CoinsGuiManager guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dataStorage = new DataStorage(this);
        transactionLogger = new TransactionLogger(this);
        currencyManager = new CurrencyManager(dataStorage);
        exchangeManager = new ExchangeManager();

        currencyManager.load(getConfig());
        exchangeManager.load(getConfig());
        transactionLogger.setEnabled(getConfig().getBoolean("log-transactions", true));

        CoinForgeAPI.init(currencyManager);

        interestManager = new InterestManager(this, currencyManager, dataStorage, transactionLogger);
        interestManager.restart(getConfig());

        registerVaultEconomyIfNeeded();

        guiManager = new CoinsGuiManager(this, currencyManager);
        getServer().getPluginManager().registerEvents(new CoinsGuiListener(guiManager), this);

        CoinsCommand coinsCommand = new CoinsCommand(this, currencyManager, exchangeManager, transactionLogger, guiManager);
        getCommand("coins").setExecutor(coinsCommand);
        getCommand("coins").setTabCompleter(coinsCommand);

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(currencyManager), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CoinForgeExpansion(this, currencyManager).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        getLogger().info("CoinForge enabled with " + currencyManager.getAll().size() + " currencies.");
    }

    private void registerVaultEconomyIfNeeded() {
        if (!currencyManager.isVaultCurrencyEnabled()) {
            getLogger().info("Vault coin is disabled in config.yml - running in custom-currencies-only mode.");
            return;
        }
        if (!currencyManager.shouldRegisterWithVault()) {
            getLogger().info("Vault coin is enabled but register-with-vault is false - the coin works through "
                    + "/coins but is not exposed to other plugins via Vault.");
            return;
        }
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault is not installed - the vault coin will not be usable by other plugins "
                    + "until Vault is installed and the server is restarted.");
            return;
        }

        ServicePriority priority = parsePriority(currencyManager.getVaultPriority());

        coinForgeEconomy = new CoinForgeEconomy(currencyManager);
        getServer().getServicesManager().register(Economy.class, coinForgeEconomy, this, priority);
        getLogger().info("Registered CoinForge as the server's Vault economy provider (priority: " + priority
                + "). Other economy plugins are now overridden for the '" + currencyManager.getVaultCurrencyId()
                + "' coin.");
    }

    private ServicePriority parsePriority(String raw) {
        if (raw != null) {
            for (ServicePriority p : ServicePriority.values()) {
                if (p.name().equalsIgnoreCase(raw)) {
                    return p;
                }
            }
        }
        getLogger().warning("Invalid vault-currency.priority in config.yml, defaulting to High.");
        return ServicePriority.High;
    }

    @Override
    public void onDisable() {
        if (interestManager != null) {
            interestManager.stop();
        }
        if (coinForgeEconomy != null) {
            getServer().getServicesManager().unregister(Economy.class, coinForgeEconomy);
        }
        if (dataStorage != null) {
            dataStorage.save();
        }
        CoinForgeAPI.shutdown();
    }

    /** Reloads config.yml and re-applies currencies/exchange rates/logging/interest without a full restart. */
    public void reload() {
        reloadConfig();
        currencyManager.load(getConfig());
        exchangeManager.load(getConfig());
        transactionLogger.setEnabled(getConfig().getBoolean("log-transactions", true));
        interestManager.restart(getConfig());
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    public DataStorage getDataStorage() {
        return dataStorage;
    }

    public ExchangeManager getExchangeManager() {
        return exchangeManager;
    }

    public TransactionLogger getTransactionLogger() {
        return transactionLogger;
    }
}
