package dev.shopforge.plugin.gui;

import dev.shopforge.plugin.model.ShopItem;

import java.util.HashMap;
import java.util.Map;

public class GuiSession {

    public enum Type {
        MAIN,
        CATEGORY,
        SEARCH
    }

    private final Type type;
    private final String categoryId; // set for CATEGORY, null otherwise
    private final String query;      // set for SEARCH, null otherwise
    private final int page;

    private final Map<Integer, String> categorySlots = new HashMap<>(); // MAIN: slot -> categoryId
    private final Map<Integer, ShopItem> itemSlots = new HashMap<>();   // CATEGORY/SEARCH: slot -> item
    private final Map<Integer, String> navSlots = new HashMap<>();      // slot -> "back"/"prev"/"next"/"search"/"balance"/"close"

    public GuiSession(Type type, String categoryId, String query, int page) {
        this.type = type;
        this.categoryId = categoryId;
        this.query = query;
        this.page = page;
    }

    public Type getType() {
        return type;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getQuery() {
        return query;
    }

    public int getPage() {
        return page;
    }

    public Map<Integer, String> getCategorySlots() {
        return categorySlots;
    }

    public Map<Integer, ShopItem> getItemSlots() {
        return itemSlots;
    }

    public Map<Integer, String> getNavSlots() {
        return navSlots;
    }
}
