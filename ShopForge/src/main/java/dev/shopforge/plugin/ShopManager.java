package dev.shopforge.plugin;

import dev.shopforge.plugin.model.Category;
import dev.shopforge.plugin.model.ShopItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ShopManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Category> categories = new LinkedHashMap<>();

    public ShopManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shops.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("shops.yml", false);
        }
        categories.clear();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection categoriesSection = yaml.getConfigurationSection("categories");
        if (categoriesSection == null) return;

        for (String categoryId : categoriesSection.getKeys(false)) {
            ConfigurationSection catSection = categoriesSection.getConfigurationSection(categoryId);
            if (catSection == null) continue;

            String displayName = color(catSection.getString("display-name", categoryId));
            Material icon = parseMaterial(catSection.getString("icon", "CHEST"), Material.CHEST);

            Category category = new Category(categoryId, displayName, icon);

            ConfigurationSection itemsSection = catSection.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemId : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                    if (itemSection == null) continue;

                    Material material = parseMaterial(itemSection.getString("material", "STONE"), Material.STONE);
                    String itemDisplayName = color(itemSection.getString("display-name", itemId));

                    List<String> lore = new ArrayList<>();
                    for (String line : itemSection.getStringList("lore")) {
                        lore.add(color(line));
                    }

                    Double buyPrice = itemSection.contains("buy-price") ? itemSection.getDouble("buy-price") : null;
                    Double sellPrice = itemSection.contains("sell-price") ? itemSection.getDouble("sell-price") : null;
                    String currencyId = itemSection.getString("currency", "coins");

                    category.addItem(new ShopItem(itemId, material, itemDisplayName, lore, buyPrice, sellPrice, currencyId));
                }
            }

            categories.put(categoryId, category);
            registerCategoryPermission(categoryId);
        }
    }

    /** Registers shopforge.category.<id> as default:true so category access can be revoked per-player. */
    private void registerCategoryPermission(String categoryId) {
        try {
            plugin.getServer().getPluginManager().addPermission(
                    new Permission("shopforge.category." + categoryId, PermissionDefault.TRUE));
        } catch (IllegalArgumentException alreadyRegistered) {
            // fine on reload - the permission already exists from a previous load()
        }
    }

    private Material parseMaterial(String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Unknown material '" + name + "' in shops.yml, using " + fallback);
            return fallback;
        }
    }

    private String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    public Map<String, Category> getCategories() {
        return categories;
    }

    public Category getCategory(String id) {
        return categories.get(id);
    }
}
