package dev.coinforge.plugin.gui;

import java.util.HashMap;
import java.util.Map;

public class CoinsGuiSession {

    public enum Type {
        WALLET,
        LEADERBOARD
    }

    private final Type type;
    private final String currencyId; // set for LEADERBOARD, null for WALLET
    private final int page;

    private final Map<Integer, String> currencySlots = new HashMap<>(); // WALLET: slot -> currencyId
    private final Map<Integer, String> navSlots = new HashMap<>();      // slot -> "back"/"refresh"/"close"

    public CoinsGuiSession(Type type, String currencyId, int page) {
        this.type = type;
        this.currencyId = currencyId;
        this.page = page;
    }

    public Type getType() {
        return type;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public int getPage() {
        return page;
    }

    public Map<Integer, String> getCurrencySlots() {
        return currencySlots;
    }

    public Map<Integer, String> getNavSlots() {
        return navSlots;
    }
}
