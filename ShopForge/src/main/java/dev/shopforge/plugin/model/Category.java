package dev.shopforge.plugin.model;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

public class Category {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final Map<String, ShopItem> items = new LinkedHashMap<>();

    public Category(String id, String displayName, Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public Map<String, ShopItem> getItems() {
        return items;
    }

    public void addItem(ShopItem item) {
        items.put(item.getId(), item);
    }
}
