package dev.shopforge.plugin;

import dev.coinforge.plugin.api.CoinForgeAPI;
import dev.shopforge.plugin.commands.ShopCommand;
import dev.shopforge.plugin.dialog.DialogSupport;
import dev.shopforge.plugin.gui.ShopGuiListener;
import dev.shopforge.plugin.gui.ShopGuiManager;
import dev.shopforge.plugin.model.Category;
import dev.shopforge.plugin.model.ShopItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopForge extends JavaPlugin {

    private ShopManager shopManager;
    private ShopGuiManager guiManager;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("CoinForge") == null) {
            getLogger().severe("CoinForge is not installed - disabling ShopForge.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        shopManager = new ShopManager(this);
        shopManager.load();

        guiManager = new ShopGuiManager(this, shopManager);

        ShopCommand shopCommand = new ShopCommand(this, guiManager);
        getCommand("shop").setExecutor(shopCommand);
        getCommand("shop").setTabCompleter(shopCommand);

        getServer().getPluginManager().registerEvents(new ShopGuiListener(this, guiManager), this);

        if (DialogSupport.isSupported()) {
            getLogger().info("Native Dialog menus detected (Paper 1.21.7+) - search and big-purchase "
                    + "confirmations will use them instead of chat prompts.");
        } else {
            getLogger().info("Native Dialog menus are not available on this server - falling back to "
                    + "chat-prompt search and click-twice purchase confirmations.");
        }

        getLogger().info("ShopForge enabled with " + shopManager.getCategories().size()
                + " categories, using CoinForge for currency.");
    }

    /** Reloads config.yml and shops.yml without a full restart. */
    public void reload() {
        reloadConfig();
        shopManager.load();
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public String formatCurrency(String currencyId, double amount) {
        CoinForgeAPI api = CoinForgeAPI.get();
        return api == null ? String.valueOf(amount) : api.format(currencyId, amount);
    }

    /** Sells the player's whole held stack if a matching sellable shop item exists. */
    public void sellHeldItem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You're not holding anything.");
            return;
        }

        ShopItem match = findSellableItem(held.getType());
        if (match == null) {
            player.sendMessage(ChatColor.RED + "That item can't be sold here.");
            return;
        }

        CoinForgeAPI api = CoinForgeAPI.get();
        if (api == null) {
            player.sendMessage(ChatColor.RED + "CoinForge is not available right now.");
            return;
        }

        int quantity = held.getAmount();
        double total = match.getSellPrice() * quantity;

        player.getInventory().setItemInMainHand(null);
        api.deposit(player, match.getCurrencyId(), total);

        player.sendMessage(ChatColor.GREEN + "Sold " + quantity + "x " + ChatColor.stripColor(match.getDisplayName())
                + " for " + api.format(match.getCurrencyId(), total) + ".");
    }

    private ShopItem findSellableItem(Material material) {
        for (Category category : shopManager.getCategories().values()) {
            for (ShopItem item : category.getItems().values()) {
                if (item.getMaterial() == material && item.isSellable()) {
                    return item;
                }
            }
        }
        return null;
    }
}
