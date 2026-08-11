package dev.coinforge.plugin.currency;

import org.bukkit.Material;

/**
 * Represents a single currency known to CoinForge. There will be exactly one
 * currency of type VAULT (optionally registered with Vault) and any number
 * of type CUSTOM. Both types are stored and managed identically internally.
 */
public class Currency {

    public enum Type {
        VAULT,
        CUSTOM
    }

    private final String id;
    private final Type type;
    private final String displayName;
    private final String symbol;
    private final int decimalPlaces;
    private final double startingBalance;
    private final Material icon;

    public Currency(String id, Type type, String displayName, String symbol, int decimalPlaces,
                     double startingBalance, Material icon) {
        this.id = id.toLowerCase();
        this.type = type;
        this.displayName = displayName;
        this.symbol = symbol;
        this.decimalPlaces = Math.max(0, decimalPlaces);
        this.startingBalance = startingBalance;
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public double getStartingBalance() {
        return startingBalance;
    }

    /** GUI icon for this coin, used by the /coins wallet and leaderboard menus. */
    public Material getIcon() {
        return icon;
    }

    /** Formats an amount using this currency's symbol and decimal places, e.g. "$1,250.00". */
    public String format(double amount) {
        String pattern = "%,." + decimalPlaces + "f";
        return symbol + String.format(pattern, amount);
    }
}

