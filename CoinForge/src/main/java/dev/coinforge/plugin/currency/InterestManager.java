package dev.coinforge.plugin.currency;

import dev.coinforge.plugin.storage.DataStorage;
import dev.coinforge.plugin.storage.TransactionLogger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Periodically pays every eligible account a percentage of its balance as
 * interest, per currency. Reads its settings fresh from config.yml each time
 * it's (re)started, so /coins reload picks up changes immediately.
 */
public class InterestManager {

    private final JavaPlugin plugin;
    private final CurrencyManager currencyManager;
    private final DataStorage dataStorage;
    private final TransactionLogger transactionLogger;

    private BukkitTask task;

    public InterestManager(JavaPlugin plugin, CurrencyManager currencyManager, DataStorage dataStorage,
                            TransactionLogger transactionLogger) {
        this.plugin = plugin;
        this.currencyManager = currencyManager;
        this.dataStorage = dataStorage;
        this.transactionLogger = transactionLogger;
    }

    /** Cancels any running schedule and starts a new one based on the current config.yml values. */
    public void restart(FileConfiguration config) {
        stop();

        if (!config.getBoolean("interest.enabled", false)) {
            return;
        }

        long intervalMinutes = Math.max(1, config.getLong("interest.interval-minutes", 60));
        long periodTicks = intervalMinutes * 60L * 20L;

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> payInterest(plugin.getConfig()),
                periodTicks, periodTicks);
        plugin.getLogger().info("Interest payouts scheduled every " + intervalMinutes + " minute(s).");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void payInterest(FileConfiguration config) {
        double ratePercent = config.getDouble("interest.rate-percent", 1.0);
        double rate = ratePercent / 100.0;
        double minimumBalance = config.getDouble("interest.minimum-balance", 0.0);
        boolean onlyOnline = config.getBoolean("interest.only-online-players", false);
        List<String> currencyIds = config.getStringList("interest.currencies");

        for (String currencyId : currencyIds) {
            Currency currency = currencyManager.get(currencyId);
            if (currency == null) continue;

            Map<UUID, Double> balances = dataStorage.getBalances(currency.getId());
            int paidCount = 0;
            double totalPaid = 0.0;

            for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
                UUID uuid = entry.getKey();
                double balance = entry.getValue();
                if (balance < minimumBalance) continue;

                Player online = Bukkit.getPlayer(uuid);
                if (onlyOnline && online == null) continue;

                double interestAmount = balance * rate;
                if (interestAmount <= 0) continue;

                OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayer(uuid);
                currencyManager.deposit(target, currency.getId(), interestAmount);
                paidCount++;
                totalPaid += interestAmount;

                if (online != null) {
                    online.sendMessage(ChatColor.GREEN + "You earned " + currency.format(interestAmount)
                            + " interest on your " + currency.getDisplayName() + " balance.");
                }
            }

            if (paidCount > 0) {
                transactionLogger.log("INTEREST paid " + paidCount + " account(s) a total of "
                        + currency.format(totalPaid) + " (" + currency.getId() + ")");
            }
        }
    }
}
