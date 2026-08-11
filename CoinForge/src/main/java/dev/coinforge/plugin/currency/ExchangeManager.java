package dev.coinforge.plugin.currency;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reads the exchange.rates section of config.yml and looks up conversion rates between coins. */
public class ExchangeManager {

    private boolean enabled;
    private final Map<String, Double> rates = new LinkedHashMap<>();

    public void load(FileConfiguration config) {
        rates.clear();
        ConfigurationSection section = config.getConfigurationSection("exchange");
        if (section == null) {
            enabled = false;
            return;
        }
        enabled = section.getBoolean("enabled", false);

        ConfigurationSection ratesSection = section.getConfigurationSection("rates");
        if (ratesSection != null) {
            for (String key : ratesSection.getKeys(false)) {
                rates.put(key.toLowerCase(), ratesSection.getDouble(key));
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Returns the rate for converting 1 unit of fromId into toId units, or null if no rate is configured. */
    public Double getRate(String fromId, String toId) {
        return rates.get(fromId.toLowerCase() + "-to-" + toId.toLowerCase());
    }
}
