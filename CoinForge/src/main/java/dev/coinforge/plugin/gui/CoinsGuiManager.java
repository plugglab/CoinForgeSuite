package dev.coinforge.plugin.gui;

import dev.coinforge.plugin.CoinForge;
import dev.coinforge.plugin.currency.Currency;
import dev.coinforge.plugin.currency.CurrencyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import java.util.stream.Collectors;

public class CoinsGuiManager {

    private final CoinForge plugin;
    private final CurrencyManager currencyManager;
    private final Map<UUID, CoinsGuiSession> sessions = new ConcurrentHashMap<>();

    public CoinsGuiManager(CoinForge plugin, CurrencyManager currencyManager) {
        this.plugin = plugin;
        this.currencyManager = currencyManager;
    }

    public CoinsGuiSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void clearSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public void openWallet(Player player) {
        int size = 27;
        String title = color("&6&lYour Balances");
        Inventory inv = plugin.getServer().createInventory(new CoinsInventoryHolder(), size, title);
        CoinsGuiSession session = new CoinsGuiSession(CoinsGuiSession.Type.WALLET, null, 0);

        fillBorder(inv, size, Material.YELLOW_STAINED_GLASS_PANE);

        int[] interior = interiorSlotsForRow1();
        int i = 0;
        for (Currency currency : currencyManager.getAll().values()) {
            if (i >= interior.length) break;
            int slot = interior[i];
            double balance = currencyManager.getBalance(player, currency.getId());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Balance: " + ChatColor.WHITE + currency.format(balance));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to view the leaderboard");

            inv.setItem(slot, buildIcon(currency.getIcon(), ChatColor.GOLD + "" + ChatColor.BOLD + currency.getDisplayName(), lore));
            session.getCurrencySlots().put(slot, currency.getId());
            i++;
        }

        List<String> closeLore = new ArrayList<>();
        closeLore.add(ChatColor.GRAY + "Click to close.");
        inv.setItem(22, buildIcon(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Close", closeLore));
        session.getNavSlots().put(22, "close");

        // Open first, THEN store the session - see ShopForge's ShopGuiManager
        // for the full explanation of why this ordering matters.
        player.openInventory(inv);
        sessions.put(player.getUniqueId(), session);
    }

    public void openLeaderboard(Player player, String currencyId, int page) {
        Currency currency = currencyManager.get(currencyId);
        if (currency == null) {
            openWallet(player);
            return;
        }

        int size = 54;
        String title = color("&6&lTop " + ChatColor.stripColor(currency.getDisplayName()));
        Inventory inv = plugin.getServer().createInventory(new CoinsInventoryHolder(), size, title);
        CoinsGuiSession session = new CoinsGuiSession(CoinsGuiSession.Type.LEADERBOARD, currencyId, page);

        fillBorder(inv, size, Material.YELLOW_STAINED_GLASS_PANE);
        int[] interior = interiorSlotsFor6Rows();
        for (int slot : interior) {
            inv.setItem(slot, fillerIcon(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
        }

        List<Map.Entry<UUID, Double>> ranking = plugin.getDataStorage().getBalances(currencyId).entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(interior.length)
                .collect(Collectors.toList());

        for (int i = 0; i < ranking.size() && i < interior.length; i++) {
            Map.Entry<UUID, Double> entry = ranking.get(i);
            inv.setItem(interior[i], buildRankIcon(i + 1, entry.getKey(), entry.getValue(), currency));
        }

        if (ranking.isEmpty()) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "No accounts found yet.");
            inv.setItem(interior[0], buildIcon(Material.BARRIER, ChatColor.RED + "No Entries", lore));
        }

        int navRowStart = size - 9;
        fillRow(inv, navRowStart, Material.BLACK_STAINED_GLASS_PANE);

        List<String> backLore = new ArrayList<>();
        backLore.add(ChatColor.GRAY + "Back to your wallet.");
        inv.setItem(navRowStart, buildIcon(Material.ARROW, ChatColor.YELLOW + "« Back", backLore));
        session.getNavSlots().put(navRowStart, "back");

        List<String> refreshLore = new ArrayList<>();
        refreshLore.add(ChatColor.GRAY + "Recalculate the rankings.");
        inv.setItem(navRowStart + 4, buildIcon(Material.NETHER_STAR, ChatColor.AQUA + "" + ChatColor.BOLD + "Refresh", refreshLore));
        session.getNavSlots().put(navRowStart + 4, "refresh");

        List<String> closeLore = new ArrayList<>();
        closeLore.add(ChatColor.GRAY + "Click to close.");
        inv.setItem(navRowStart + 8, buildIcon(Material.BARRIER, ChatColor.RED + "" + ChatColor.BOLD + "Close", closeLore));
        session.getNavSlots().put(navRowStart + 8, "close");

        player.openInventory(inv);
        sessions.put(player.getUniqueId(), session);
    }

    public void refresh(Player player, CoinsGuiSession session) {
        if (session.getType() == CoinsGuiSession.Type.WALLET) {
            openWallet(player);
        } else {
            openLeaderboard(player, session.getCurrencyId(), session.getPage());
        }
    }

    private ItemStack buildRankIcon(int rank, UUID uuid, double balance, Currency currency) {
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
        String name = target.getName() != null ? target.getName() : uuid.toString().substring(0, 8);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta) {
            ((SkullMeta) meta).setOwningPlayer(target);
        }
        if (meta != null) {
            ChatColor rankColor = rank == 1 ? ChatColor.GOLD : rank == 2 ? ChatColor.WHITE : rank == 3 ? ChatColor.RED : ChatColor.GRAY;
            meta.setDisplayName(rankColor + "" + ChatColor.BOLD + "#" + rank + " " + ChatColor.RESET + rankColor + name);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Balance: " + ChatColor.WHITE + currency.format(balance));
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
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

    /** Interior slots of a 3-row (27 slot) inventory: row 1, columns 1-7. */
    private int[] interiorSlotsForRow1() {
        int[] slots = new int[7];
        for (int col = 1; col <= 7; col++) {
            slots[col - 1] = 9 + col;
        }
        return slots;
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

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
