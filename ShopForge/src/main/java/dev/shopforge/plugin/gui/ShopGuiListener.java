package dev.shopforge.plugin.gui;

import dev.coinforge.plugin.api.CoinForgeAPI;
import dev.shopforge.plugin.ShopForge;
import dev.shopforge.plugin.dialog.DialogSupport;
import dev.shopforge.plugin.model.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShopGuiListener implements Listener {

    private final ShopForge plugin;
    private final ShopGuiManager guiManager;
    private final Map<UUID, PendingAction> pending = new HashMap<>();
    private final Map<UUID, Long> awaitingSearch = new HashMap<>();
    private static final long SEARCH_PROMPT_TIMEOUT_MS = 30_000L;

    public ShopGuiListener(ShopForge plugin, ShopGuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopInventoryHolder)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Cancel every click while a ShopForge GUI is open, including shift-clicks
        // from the player's own inventory, so nothing can be taken or moved.
        event.setCancelled(true);

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(clicked.getHolder() instanceof ShopInventoryHolder)) {
            return; // click landed in the player's own inventory - ignore
        }

        Player player = (Player) event.getWhoClicked();
        GuiSession session = guiManager.getSession(player);
        if (session == null) return;

        int slot = event.getSlot();

        if (session.getType() == GuiSession.Type.MAIN) {
            String categoryId = session.getCategorySlots().get(slot);
            if (categoryId != null) {
                guiManager.openCategory(player, categoryId, 0);
                return;
            }
            String mainNav = session.getNavSlots().get(slot);
            if ("search".equals(mainNav)) {
                promptSearch(player);
            } else if ("balance".equals(mainNav)) {
                guiManager.refresh(player, session);
            } else if ("close".equals(mainNav)) {
                player.closeInventory();
            }
            return;
        }

        String nav = session.getNavSlots().get(slot);
        if (nav != null) {
            switch (nav) {
                case "back":
                    guiManager.openMain(player);
                    break;
                case "prev":
                    if (session.getType() == GuiSession.Type.SEARCH) {
                        guiManager.openSearchResults(player, session.getQuery(), session.getPage() - 1);
                    } else {
                        guiManager.openCategory(player, session.getCategoryId(), session.getPage() - 1);
                    }
                    break;
                case "next":
                    if (session.getType() == GuiSession.Type.SEARCH) {
                        guiManager.openSearchResults(player, session.getQuery(), session.getPage() + 1);
                    } else {
                        guiManager.openCategory(player, session.getCategoryId(), session.getPage() + 1);
                    }
                    break;
                case "balance":
                    guiManager.refresh(player, session);
                    break;
                case "close":
                    player.closeInventory();
                    break;
                default:
                    break;
            }
            return;
        }

        ShopItem item = session.getItemSlots().get(slot);
        if (item == null) return;

        if (event.isRightClick()) {
            handleSell(player, item, event.isShiftClick());
        } else {
            handleBuy(player, item, event.isShiftClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        // Dragging a stack across slots (including shift-drag/split-drops) was never
        // being cancelled here before, so an item dragged into the shop's fake
        // inventory could get swallowed and lost permanently when the GUI closed.
        // Block it entirely - nothing should ever be able to enter a ShopForge
        // inventory this way.
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopInventoryHolder)) return;
        if (event.getPlayer() instanceof Player) {
            guiManager.clearSession((Player) event.getPlayer());
        }
    }

    private boolean useNativeDialogs() {
        return plugin.getConfig().getBoolean("use-native-dialogs", true) && DialogSupport.isSupported();
    }

    private void promptSearch(Player player) {
        if (useNativeDialogs()) {
            player.closeInventory();
            DialogSupport.openSearchDialog(plugin, guiManager, player);
            return;
        }
        player.closeInventory();
        awaitingSearch.put(player.getUniqueId(), System.currentTimeMillis());
        String prompt = plugin.getConfig().getString("messages.search-prompt",
                "&eType the item name in chat, or 'cancel' to abort.");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', prompt));
    }

    // LOWEST priority so we intercept and cancel before chat-formatting/logging plugins see it.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Long promptedAt = awaitingSearch.get(player.getUniqueId());
        if (promptedAt == null) return;

        // Stale prompt (player never followed up) - let this chat message through normally.
        if (System.currentTimeMillis() - promptedAt > SEARCH_PROMPT_TIMEOUT_MS) {
            awaitingSearch.remove(player.getUniqueId());
            return;
        }

        event.setCancelled(true);
        awaitingSearch.remove(player.getUniqueId());
        String query = event.getMessage().trim();

        // Bukkit API calls aren't safe from this event's async thread - hop back to the main thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (query.equalsIgnoreCase("cancel")) {
                sendMessage(player, "search-cancelled");
                return;
            }
            guiManager.openSearchResults(player, query, 0);
        });
    }

    private void handleBuy(Player player, ShopItem item, boolean shift) {
        if (!item.isBuyable()) {
            sendMessage(player, "no-buy-price");
            return;
        }
        CoinForgeAPI api = CoinForgeAPI.get();
        if (api == null) {
            player.sendMessage(ChatColor.RED + "CoinForge is not available right now.");
            return;
        }

        int quantity = shift ? Math.max(1, item.getMaterial().getMaxStackSize()) : 1;
        double totalPrice = item.getBuyPrice() * quantity;
        String formatted = api.format(item.getCurrencyId(), totalPrice);

        Runnable action = () -> executeBuy(player, item, quantity, totalPrice, formatted, api);

        double threshold = plugin.getConfig().getDouble("confirm-purchases.threshold", -1);
        if (threshold >= 0 && totalPrice >= threshold) {
            String detail = "Buy " + quantity + "x " + ChatColor.stripColor(item.getDisplayName()) + " for " + formatted + "?";
            if (useNativeDialogs()) {
                DialogSupport.openConfirmDialog(plugin, player, "Confirm Purchase", detail, action);
                return;
            }
            if (awaitingClickTwiceConfirmation(player, "buy:" + item.getId() + ":" + quantity, formatted)) {
                return;
            }
        }

        action.run();
    }

    private void executeBuy(Player player, ShopItem item, int quantity, double totalPrice, String formatted, CoinForgeAPI api) {
        if (!api.has(player, item.getCurrencyId(), totalPrice)) {
            sendMessage(player, "not-enough-funds", "%currency%", api.getDisplayName(item.getCurrencyId()));
            return;
        }

        api.withdraw(player, item.getCurrencyId(), totalPrice);

        ItemStack stack = new ItemStack(item.getMaterial(), quantity);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        if (!leftover.isEmpty()) {
            sendMessage(player, "inventory-full");
        }

        playSound(player, "buy-sound");
        sendMessage(player, "bought",
                "%amount%", String.valueOf(quantity),
                "%item%", ChatColor.stripColor(item.getDisplayName()),
                "%price%", formatted);
    }

    private void handleSell(Player player, ShopItem item, boolean shift) {
        if (!item.isSellable()) {
            sendMessage(player, "no-sell-price");
            return;
        }
        CoinForgeAPI api = CoinForgeAPI.get();
        if (api == null) {
            player.sendMessage(ChatColor.RED + "CoinForge is not available right now.");
            return;
        }

        int owned = countItems(player.getInventory(), item.getMaterial());
        int quantity = shift ? owned : Math.min(1, owned);

        if (quantity <= 0) {
            sendMessage(player, "not-enough-items");
            return;
        }

        double totalPrice = item.getSellPrice() * quantity;
        String formatted = api.format(item.getCurrencyId(), totalPrice);

        Runnable action = () -> executeSell(player, item, quantity, totalPrice, formatted, api);

        double threshold = plugin.getConfig().getDouble("confirm-purchases.threshold", -1);
        if (threshold >= 0 && totalPrice >= threshold) {
            String detail = "Sell " + quantity + "x " + ChatColor.stripColor(item.getDisplayName()) + " for " + formatted + "?";
            if (useNativeDialogs()) {
                DialogSupport.openConfirmDialog(plugin, player, "Confirm Sale", detail, action);
                return;
            }
            if (awaitingClickTwiceConfirmation(player, "sell:" + item.getId() + ":" + quantity, formatted)) {
                return;
            }
        }

        action.run();
    }

    private void executeSell(Player player, ShopItem item, int quantity, double totalPrice, String formatted, CoinForgeAPI api) {
        removeItems(player.getInventory(), item.getMaterial(), quantity);
        api.deposit(player, item.getCurrencyId(), totalPrice);

        playSound(player, "sell-sound");
        sendMessage(player, "sold",
                "%amount%", String.valueOf(quantity),
                "%item%", ChatColor.stripColor(item.getDisplayName()),
                "%price%", formatted);
    }

    private int countItems(PlayerInventory inv, Material material) {
        int count = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private void removeItems(PlayerInventory inv, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
        }
        inv.setContents(contents);
    }

    /** Fallback confirmation for servers without native dialogs: click again within the timeout to confirm. */
    private boolean awaitingClickTwiceConfirmation(Player player, String actionKey, String formattedPrice) {
        int timeoutSeconds = plugin.getConfig().getInt("confirm-purchases.timeout-seconds", 5);
        long now = System.currentTimeMillis();
        PendingAction existing = pending.get(player.getUniqueId());

        if (existing != null && existing.key.equals(actionKey) && (now - existing.timestamp) <= timeoutSeconds * 1000L) {
            pending.remove(player.getUniqueId());
            return false; // confirmed - let the action through
        }

        pending.put(player.getUniqueId(), new PendingAction(actionKey, now));
        sendMessage(player, "confirm", "%price%", formattedPrice, "%seconds%", String.valueOf(timeoutSeconds));
        return true;
    }

    private void playSound(Player player, String configKey) {
        String soundName = plugin.getConfig().getString(configKey, "");
        if (soundName == null || soundName.isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException ignored) {
            // invalid/renamed sound name on this server version - skip silently
        }
    }

    private void sendMessage(Player player, String key, String... replacements) {
        String msg = plugin.getConfig().getString("messages." + key, "");
        if (msg == null || msg.isEmpty()) return;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private static class PendingAction {
        final String key;
        final long timestamp;

        PendingAction(String key, long timestamp) {
            this.key = key;
            this.timestamp = timestamp;
        }
    }
}
