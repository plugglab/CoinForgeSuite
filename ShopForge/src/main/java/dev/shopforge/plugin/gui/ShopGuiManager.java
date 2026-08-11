package dev.shopforge.plugin.gui;

import dev.coinforge.plugin.api.CoinForgeAPI;
import dev.shopforge.plugin.ShopForge;
import dev.shopforge.plugin.ShopManager;
import dev.shopforge.plugin.model.Category;
import dev.shopforge.plugin.model.ShopItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopGuiManager {

    private static final int MAIN_SIZE = 54; // fixed 6 rows for a consistent bordered layout

    private final ShopForge plugin;
    private final ShopManager shopManager;
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();

    public ShopGuiManager(ShopForge plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    public GuiSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void clearSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public void openMain(Player player) {
        List<Category> visible = new ArrayList<>();
        for (Category category : shopManager.getCategories().values()) {
            if (player.hasPermission("shopforge.category." + category.getId())) {
                visible.add(category);
            }
        }

        String title = color(plugin.getConfig().getString("gui-title", "&8Shop"));
        Inventory inv = plugin.getServer().createInventory(new ShopInventoryHolder(), MAIN_SIZE, title);
        GuiSession session = new GuiSession(GuiSession.Type.MAIN, null, null, 0);

        // Blue-themed border frame all the way around, distinguishing the main
        // menu at a glance from category pages (which use black/gray).
        fillBorder(inv, MAIN_SIZE, Material.BLUE_STAINED_GLASS_PANE);
        int[] interiorSlots = interiorSlotsFor6Rows();
        for (int slot : interiorSlots) {
            inv.setItem(slot, fillerIcon(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        }

        int i = 0;
        for (Category category : visible) {
            if (i >= interiorSlots.length) break;
            int slot = interiorSlots[i];
            inv.setItem(slot, buildIcon(category.getIcon(), category.getDisplayName(), null));
            session.getCategorySlots().put(slot, category.getId());
            i++;
        }

        // Footer row (slots 45-53): search / balance head / close, centered.
        List<String> searchLore = new ArrayList<>();
        searchLore.add(ChatColor.GRAY + "Click, then type an item name in chat.");
        String searchIconMaterial = plugin.getConfig().getString("search-icon", "COMPASS");
        inv.setItem(48, buildIcon(parseMaterial(searchIconMaterial), ChatColor.AQUA + "" + ChatColor.BOLD + "Search", searchLore));
        session.getNavSlots().put(48, "search");

        inv.setItem(49, buildBalanceHead(player));
        session.getNavSlots().put(49, "balance");

        List<String> closeLore = new ArrayList<>();
        closeLore.add(ChatColor.GRAY + "Click to close the shop.");
        inv.setItem(50, buildIcon(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Close", closeLore));
        session.getNavSlots().put(50, "close");

        // Open first, THEN store the session - opening while another of our
        // inventories is already open fires an implicit close event for the
        // old one first, which clears whatever session is in the map for this
        // player. Storing the new session before that close event fires meant
        // it got wiped immediately, silently breaking every click afterward.
        player.openInventory(inv);
        sessions.put(player.getUniqueId(), session);
    }

    public void openCategory(Player player, String categoryId, int page) {
        Category category = shopManager.getCategory(categoryId);
        if (category == null) {
            openMain(player);
            return;
        }
        if (!player.hasPermission("shopforge.category." + categoryId)) {
            player.sendMessage(ChatColor.RED + "You don't have access to that category.");
            openMain(player);
            return;
        }

        List<ShopItem> items = new ArrayList<>(category.getItems().values());
        openPagedItemMenu(player, GuiSession.Type.CATEGORY, categoryId, null, items, page, category.getDisplayName());
    }

    /** Searches every item across every category the player can access, by ID or display name. */
    public void openSearchResults(Player player, String query, int page) {
        String needle = ChatColor.stripColor(query).toLowerCase();
        List<ShopItem> matches = new ArrayList<>();

        for (Category category : shopManager.getCategories().values()) {
            if (!player.hasPermission("shopforge.category." + category.getId())) continue;
            for (ShopItem item : category.getItems().values()) {
                String plainName = ChatColor.stripColor(item.getDisplayName()).toLowerCase();
                if (item.getId().toLowerCase().contains(needle) || plainName.contains(needle)) {
                    matches.add(item);
                }
            }
        }

        if (matches.isEmpty()) {
            String msg = plugin.getConfig().getString("messages.search-no-results", "&cNo items found matching '%query%'.")
                    .replace("%query%", query);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }

        openPagedItemMenu(player, GuiSession.Type.SEARCH, null, query, matches, page, "&8Search: &f" + query);
    }

    /** Refreshes whatever screen the player currently has open (used by the balance head's click-to-refresh). */
    public void refresh(Player player, GuiSession session) {
        switch (session.getType()) {
            case MAIN:
                openMain(player);
                break;
            case CATEGORY:
                openCategory(player, session.getCategoryId(), session.getPage());
                break;
            case SEARCH:
                openSearchResults(player, session.getQuery(), session.getPage());
                break;
            default:
                openMain(player);
        }
    }

    /** Shared builder for any paginated list of ShopItems (category browsing or search results). */
    private void openPagedItemMenu(Player player, GuiSession.Type type, String categoryId, String query,
                                    List<ShopItem> items, int page, String titlePrefix) {
        int rowsPerPage = clamp(plugin.getConfig().getInt("rows-per-page", 5), 1, 5);
        int itemsPerPage = rowsPerPage * 9;
        int size = (rowsPerPage + 1) * 9; // + 1 footer row

        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) itemsPerPage));
        int clampedPage = clamp(page, 0, totalPages - 1);

        String title = color(titlePrefix + " &7(" + (clampedPage + 1) + "/" + totalPages + ")");
        Inventory inv = plugin.getServer().createInventory(new ShopInventoryHolder(), size, title);
        GuiSession session = new GuiSession(type, categoryId, query, clampedPage);

        int start = clampedPage * itemsPerPage;
        int end = Math.min(items.size(), start + itemsPerPage);
        int slot = 0;
        for (int idx = start; idx < end; idx++) {
            ShopItem item = items.get(idx);
            inv.setItem(slot, buildItemIcon(item));
            session.getItemSlots().put(slot, item);
            slot++;
        }
        // Fill any unused item slots on this page with light filler instead of leaving them blank.
        for (int s = slot; s < itemsPerPage; s++) {
            inv.setItem(s, fillerIcon(Material.GRAY_STAINED_GLASS_PANE));
        }

        int navRowStart = size - 9;
        fillRow(inv, navRowStart, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(navRowStart, buildIcon(Material.ARROW, color("&e« Back"), null));
        session.getNavSlots().put(navRowStart, "back");

        if (clampedPage > 0) {
            inv.setItem(navRowStart + 3, buildIcon(Material.ARROW, color("&e« Previous Page"), null));
            session.getNavSlots().put(navRowStart + 3, "prev");
        }

        inv.setItem(navRowStart + 4, buildBalanceHead(player));
        session.getNavSlots().put(navRowStart + 4, "balance");

        if (clampedPage < totalPages - 1) {
            inv.setItem(navRowStart + 5, buildIcon(Material.ARROW, color("&eNext Page »"), null));
            session.getNavSlots().put(navRowStart + 5, "next");
        }

        List<String> closeLore = new ArrayList<>();
        closeLore.add(ChatColor.GRAY + "Click to close the shop.");
        inv.setItem(navRowStart + 8, buildIcon(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Close", closeLore));
        session.getNavSlots().put(navRowStart + 8, "close");

        // Same fix as openMain(): open before storing the session.
        player.openInventory(inv);
        sessions.put(player.getUniqueId(), session);
    }

    /** A clickable head showing the viewer's own current balance in every coin, refreshing on click. */
    private ItemStack buildBalanceHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta) {
            ((SkullMeta) meta).setOwningPlayer(player);
        }
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + player.getName() + "'s Balances");

            List<String> lore = new ArrayList<>();
            CoinForgeAPI api = CoinForgeAPI.get();
            if (api != null) {
                for (String currencyId : api.getCurrencyIds()) {
                    double balance = api.getBalance(player, currencyId);
                    lore.add(ChatColor.YELLOW + api.getDisplayName(currencyId) + ChatColor.GRAY + ": "
                            + ChatColor.WHITE + api.format(currencyId, balance));
                }
            } else {
                lore.add(ChatColor.RED + "CoinForge is unavailable.");
            }
            lore.add("");
            lore.add(ChatColor.GRAY + "Click to refresh");

            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack buildItemIcon(ShopItem item) {
        List<String> lore = new ArrayList<>();
        if (!item.getLore().isEmpty()) {
            lore.addAll(item.getLore());
            lore.add("");
        }

        String divider = ChatColor.GRAY + "" + ChatColor.STRIKETHROUGH + "――――――――――――";
        lore.add(divider);

        if (item.isBuyable()) {
            lore.add(ChatColor.GRAY + "Buy Price: " + ChatColor.GREEN + ""
                    + plugin.formatCurrency(item.getCurrencyId(), item.getBuyPrice()) + ChatColor.DARK_GRAY + " / each");
        }
        if (item.isSellable()) {
            lore.add(ChatColor.GRAY + "Sell Price: " + ChatColor.YELLOW + ""
                    + plugin.formatCurrency(item.getCurrencyId(), item.getSellPrice()) + ChatColor.DARK_GRAY + " / each");
        }
        if (!item.isBuyable() && !item.isSellable()) {
            lore.add(ChatColor.RED + "Not currently available.");
        }

        lore.add(divider);

        if (item.isBuyable()) {
            lore.add(ChatColor.GREEN + "▸ " + ChatColor.GRAY + "Left-Click " + ChatColor.DARK_GRAY + "to buy 1");
            lore.add(ChatColor.GREEN + "▸ " + ChatColor.GRAY + "Shift-Left-Click " + ChatColor.DARK_GRAY + "to buy a stack");
        }
        if (item.isSellable()) {
            lore.add(ChatColor.YELLOW + "▸ " + ChatColor.GRAY + "Right-Click " + ChatColor.DARK_GRAY + "to sell 1");
            lore.add(ChatColor.YELLOW + "▸ " + ChatColor.GRAY + "Shift-Right-Click " + ChatColor.DARK_GRAY + "to sell all");
        }

        return buildIcon(item.getMaterial(), item.getDisplayName(), lore);
    }

    private ItemStack buildIcon(Material material, String displayName, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** A glass pane with a blank name, used purely as visual filler/border. */
    private ItemStack fillerIcon(Material paneType) {
        ItemStack stack = new ItemStack(paneType);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void fillRow(Inventory inv, int rowStartSlot, Material paneType) {
        ItemStack filler = fillerIcon(paneType);
        for (int i = 0; i < 9; i++) {
            inv.setItem(rowStartSlot + i, filler);
        }
    }

    /** Fills the outer ring of a 6-row (54 slot) inventory with the given pane color. */
    private void fillBorder(Inventory inv, int size, Material paneType) {
        ItemStack border = fillerIcon(paneType);
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(slot, border);
            }
        }
    }

    /** The 28 non-border slots of a 6-row inventory (rows 1-4, columns 1-7), in reading order. */
    private int[] interiorSlotsFor6Rows() {
        int[] slots = new int[28];
        int i = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[i++] = row * 9 + col;
            }
        }
        return slots;
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.COMPASS;
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
