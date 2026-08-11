package dev.shopforge.plugin.model;

import org.bukkit.Material;

import java.util.List;

public class ShopItem {

    private final String id;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final Double buyPrice;   // per unit; null = not buyable
    private final Double sellPrice;  // per unit; null = not sellable
    private final String currencyId;

    public ShopItem(String id, Material material, String displayName, List<String> lore,
                     Double buyPrice, Double sellPrice, String currencyId) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.currencyId = currencyId;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public Double getBuyPrice() {
        return buyPrice;
    }

    public Double getSellPrice() {
        return sellPrice;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public boolean isBuyable() {
        return buyPrice != null;
    }

    public boolean isSellable() {
        return sellPrice != null;
    }
}
