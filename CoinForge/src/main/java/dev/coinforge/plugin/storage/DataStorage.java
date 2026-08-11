package dev.coinforge.plugin.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Stores every coin's balances in its own file under plugins/CoinForge/data/,
 * e.g. data/coins.yml, data/gems.yml, data/tokens.yml. Each file is tagged
 * with a "format-version" so future CoinForge versions can migrate the
 * layout safely without guessing what shape old data is in.
 */
public class DataStorage {

    private static final String FORMAT_VERSION = "1.0";

    private final JavaPlugin plugin;
    private final File dataFolder;

    // currencyId -> uuid -> balance
    private final ConcurrentHashMap<String, ConcurrentHashMap<UUID, Double>> balances = new ConcurrentHashMap<>();

    public DataStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
    }

    /** (Re)loads a balance file for every currency ID currently configured. */
    public void load(Collection<String> currencyIds) {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        balances.clear();
        for (String id : currencyIds) {
            loadCurrency(id);
        }
    }

    private File fileFor(String currencyId) {
        return new File(dataFolder, currencyId + ".yml");
    }

    private void loadCurrency(String currencyId) {
        ConcurrentHashMap<UUID, Double> map = new ConcurrentHashMap<>();
        File file = fileFor(currencyId);

        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = yaml.getConfigurationSection("balances");
            if (section != null) {
                for (String uuidStr : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        map.put(uuid, section.getDouble(uuidStr));
                    } catch (IllegalArgumentException ignored) {
                        // skip malformed uuid entries
                    }
                }
            }
        }

        balances.put(currencyId, map);
    }

    /** Saves every currency's balance file. Called on shutdown. */
    public void save() {
        for (String currencyId : balances.keySet()) {
            saveCurrency(currencyId);
        }
    }

    /** Saves a single currency's balance file immediately. */
    public void saveCurrency(String currencyId) {
        ConcurrentHashMap<UUID, Double> map = balances.get(currencyId);
        if (map == null) return;

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format-version", FORMAT_VERSION);
        for (Map.Entry<UUID, Double> entry : map.entrySet()) {
            yaml.set("balances." + entry.getKey(), entry.getValue());
        }

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        try {
            yaml.save(fileFor(currencyId));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + currencyId + ".yml", e);
        }
    }

    public double getBalance(String currencyId, UUID uuid, double defaultValue) {
        ConcurrentHashMap<UUID, Double> map = balances.get(currencyId);
        if (map == null) return defaultValue;
        return map.getOrDefault(uuid, defaultValue);
    }

    public void setBalance(String currencyId, UUID uuid, double amount) {
        balances.computeIfAbsent(currencyId, k -> new ConcurrentHashMap<>()).put(uuid, amount);
    }

    public boolean hasAccount(String currencyId, UUID uuid) {
        ConcurrentHashMap<UUID, Double> map = balances.get(currencyId);
        return map != null && map.containsKey(uuid);
    }

    /** Returns a snapshot copy of every balance recorded for one currency, used for /coins top. */
    public Map<UUID, Double> getBalances(String currencyId) {
        ConcurrentHashMap<UUID, Double> map = balances.get(currencyId);
        return map == null ? new HashMap<>() : new HashMap<>(map);
    }
}
