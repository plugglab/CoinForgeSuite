package dev.coinforge.plugin.commands;

import dev.coinforge.plugin.CoinForge;
import dev.coinforge.plugin.currency.Currency;
import dev.coinforge.plugin.currency.CurrencyManager;
import dev.coinforge.plugin.currency.ExchangeManager;
import dev.coinforge.plugin.gui.CoinsGuiManager;
import dev.coinforge.plugin.storage.TransactionLogger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class CoinsCommand implements CommandExecutor, TabCompleter {

    private final CoinForge plugin;
    private final CurrencyManager currencyManager;
    private final ExchangeManager exchangeManager;
    private final TransactionLogger transactionLogger;
    private final CoinsGuiManager guiManager;

    private final Map<UUID, Long> lastPayTimestamp = new HashMap<>();

    public CoinsCommand(CoinForge plugin, CurrencyManager currencyManager, ExchangeManager exchangeManager,
                         TransactionLogger transactionLogger, CoinsGuiManager guiManager) {
        this.plugin = plugin;
        this.currencyManager = currencyManager;
        this.exchangeManager = exchangeManager;
        this.transactionLogger = transactionLogger;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return showOwnBalances(sender);
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                sendHelp(sender);
                return true;
            case "gui":
            case "wallet":
                return handleGui(sender);
            case "pay":
                return handlePay(sender, args);
            case "top":
                return handleTop(sender, args);
            case "exchange":
                return handleExchange(sender, args);
            case "admin":
                return handleAdmin(sender, args);
            case "reload":
                if (!sender.hasPermission("coinforge.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                    return true;
                }
                plugin.reload();
                sender.sendMessage(ChatColor.GREEN + "CoinForge configuration reloaded.");
                return true;
            default:
                if (currencyManager.exists(sub)) {
                    return showSingleBalance(sender, sub);
                }
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the balance menu; try /coins.");
            return true;
        }
        if (!sender.hasPermission("coinforge.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }
        guiManager.openWallet((Player) sender);
        return true;
    }

    private boolean showOwnBalances(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Console must specify a player, e.g. /coins admin ...");
            return true;
        }
        if (!sender.hasPermission("coinforge.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }
        Player player = (Player) sender;
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Your Balances");
        for (Currency currency : currencyManager.getAll().values()) {
            double bal = currencyManager.getBalance(player, currency.getId());
            sender.sendMessage(ChatColor.YELLOW + currency.getDisplayName() + ChatColor.GRAY + ": "
                    + ChatColor.WHITE + currency.format(bal));
        }
        return true;
    }

    private boolean showSingleBalance(CommandSender sender, String currencyId) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can check their own balance from console; try /coins admin.");
            return true;
        }
        Currency currency = currencyManager.get(currencyId);
        double bal = currencyManager.getBalance((Player) sender, currencyId);
        sender.sendMessage(ChatColor.YELLOW + currency.getDisplayName() + ChatColor.GRAY + ": "
                + ChatColor.WHITE + currency.format(bal));
        return true;
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can pay other players.");
            return true;
        }
        if (!sender.hasPermission("coinforge.pay")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /coins pay <player> <currency> <amount>");
            return true;
        }

        Player from = (Player) sender;

        int cooldown = plugin.getConfig().getInt("pay-cooldown-seconds", 0);
        if (cooldown > 0) {
            Long last = lastPayTimestamp.get(from.getUniqueId());
            if (last != null) {
                long secondsLeft = cooldown - (System.currentTimeMillis() - last) / 1000L;
                if (secondsLeft > 0) {
                    sender.sendMessage(ChatColor.RED + "You must wait " + secondsLeft + "s before paying again.");
                    return true;
                }
            }
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer to = Bukkit.getOfflinePlayer(args[1]);
        String currencyId = args[2].toLowerCase();
        double amount;

        if (!currencyManager.exists(currencyId)) {
            sender.sendMessage(ChatColor.RED + "Unknown currency: " + args[2]);
            return true;
        }
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[3]);
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be greater than zero.");
            return true;
        }
        if (to.getUniqueId().equals(from.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You can't pay yourself.");
            return true;
        }

        Currency currency = currencyManager.get(currencyId);

        if (!currencyManager.has(from, currencyId, amount)) {
            sender.sendMessage(ChatColor.RED + "You don't have enough " + currency.getDisplayName() + ".");
            return true;
        }

        currencyManager.withdraw(from, currencyId, amount);

        double taxPercent = from.hasPermission("coinforge.tax.bypass")
                ? 0.0 : plugin.getConfig().getDouble("tax.pay-percent", 0.0);
        double taxAmount = amount * (taxPercent / 100.0);
        double receivedAmount = amount - taxAmount;

        currencyManager.deposit(to, currencyId, receivedAmount);
        lastPayTimestamp.put(from.getUniqueId(), System.currentTimeMillis());

        String toName = to.getName() != null ? to.getName() : args[1];
        if (taxAmount > 0) {
            sender.sendMessage(ChatColor.GREEN + "You paid " + currency.format(amount) + " to " + toName
                    + " (" + currency.format(taxAmount) + " lost to tax).");
        } else {
            sender.sendMessage(ChatColor.GREEN + "You paid " + currency.format(amount) + " to " + toName + ".");
        }
        transactionLogger.log("PAY " + from.getName() + " -> " + toName + " : " + currency.format(amount)
                + " (" + currencyId + ")" + (taxAmount > 0 ? " [tax: " + currency.format(taxAmount) + "]" : ""));

        if (to.isOnline() && to.getPlayer() != null) {
            to.getPlayer().sendMessage(ChatColor.GREEN + "You received " + currency.format(receivedAmount)
                    + " from " + from.getName() + ".");
        }
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("coinforge.top")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /coins top <currency>");
            return true;
        }
        String currencyId = args[1].toLowerCase();
        Currency currency = currencyManager.get(currencyId);
        if (currency == null) {
            sender.sendMessage(ChatColor.RED + "Unknown currency: " + args[1]);
            return true;
        }

        if (sender instanceof Player) {
            guiManager.openLeaderboard((Player) sender, currencyId, 0);
            return true;
        }

        List<Map.Entry<UUID, Double>> ranking = plugin.getDataStorage().getBalances(currencyId).entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(10)
                .collect(Collectors.toList());

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Top " + currency.getDisplayName());
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : ranking) {
            @SuppressWarnings("deprecation")
            OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
            String name = p.getName() != null ? p.getName() : entry.getKey().toString().substring(0, 8);
            sender.sendMessage(ChatColor.YELLOW + "" + rank + ". " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " - " + ChatColor.WHITE + currency.format(entry.getValue()));
            rank++;
        }
        if (ranking.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No accounts found yet.");
        }
        return true;
    }

    private boolean handleExchange(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can exchange currency.");
            return true;
        }
        if (!sender.hasPermission("coinforge.exchange")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }
        if (!exchangeManager.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "Currency exchange is disabled on this server.");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /coins exchange <from> <to> <amount>");
            return true;
        }

        Player player = (Player) sender;
        String fromId = args[1].toLowerCase();
        String toId = args[2].toLowerCase();

        if (!currencyManager.exists(fromId) || !currencyManager.exists(toId)) {
            sender.sendMessage(ChatColor.RED + "Unknown currency.");
            return true;
        }

        Double rate = exchangeManager.getRate(fromId, toId);
        if (rate == null) {
            sender.sendMessage(ChatColor.RED + "There is no exchange rate set up between those two coins.");
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[3]);
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be greater than zero.");
            return true;
        }

        Currency from = currencyManager.get(fromId);
        Currency to = currencyManager.get(toId);

        if (!currencyManager.has(player, fromId, amount)) {
            sender.sendMessage(ChatColor.RED + "You don't have enough " + from.getDisplayName() + ".");
            return true;
        }

        double converted = amount * rate;

        double taxPercent = player.hasPermission("coinforge.tax.bypass")
                ? 0.0 : plugin.getConfig().getDouble("tax.exchange-percent", 0.0);
        double taxAmount = converted * (taxPercent / 100.0);
        double receivedAmount = converted - taxAmount;

        currencyManager.withdraw(player, fromId, amount);
        currencyManager.deposit(player, toId, receivedAmount);

        if (taxAmount > 0) {
            sender.sendMessage(ChatColor.GREEN + "Exchanged " + from.format(amount) + " for " + to.format(receivedAmount)
                    + " (" + to.format(taxAmount) + " lost to tax).");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Exchanged " + from.format(amount) + " for " + to.format(receivedAmount) + ".");
        }
        transactionLogger.log("EXCHANGE " + player.getName() + " : " + from.format(amount) + " -> " + to.format(receivedAmount)
                + (taxAmount > 0 ? " [tax: " + to.format(taxAmount) + "]" : ""));
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("coinforge.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /coins admin <give|take|set|reset> <player> <currency> [amount]");
            return true;
        }

        String action = args[1].toLowerCase();
        boolean needsAmount = !action.equals("reset");
        int minArgs = needsAmount ? 5 : 4;
        if (args.length < minArgs) {
            sender.sendMessage(ChatColor.RED + "Usage: /coins admin " + action + " <player> <currency>"
                    + (needsAmount ? " <amount>" : ""));
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        String currencyId = args[3].toLowerCase();

        if (!currencyManager.exists(currencyId)) {
            sender.sendMessage(ChatColor.RED + "Unknown currency: " + args[3]);
            return true;
        }
        Currency currency = currencyManager.get(currencyId);

        double amount = 0;
        if (needsAmount) {
            try {
                amount = Double.parseDouble(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[4]);
                return true;
            }
            if (amount < 0) {
                sender.sendMessage(ChatColor.RED + "Amount cannot be negative.");
                return true;
            }
        }

        boolean success;
        String verb;
        switch (action) {
            case "give":
                success = currencyManager.deposit(target, currencyId, amount);
                verb = "Gave";
                break;
            case "take":
                success = currencyManager.withdraw(target, currencyId, amount);
                verb = "Took";
                break;
            case "set":
                success = currencyManager.setBalance(target, currencyId, amount);
                verb = "Set";
                break;
            case "reset":
                success = currencyManager.resetBalance(target, currencyId);
                verb = "Reset";
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown action: " + action + " (use give, take, set or reset)");
                return true;
        }

        if (!success) {
            sender.sendMessage(ChatColor.RED + "That action failed (the player may not have enough funds).");
            return true;
        }

        String targetName = target.getName() != null ? target.getName() : args[2];
        String senderName = sender instanceof Player ? sender.getName() : "Console";

        if (action.equals("set")) {
            sender.sendMessage(ChatColor.GREEN + verb + " " + targetName + "'s " + currency.getDisplayName()
                    + " balance to " + currency.format(amount) + ".");
            transactionLogger.log("ADMIN SET by " + senderName + " on " + targetName + " : "
                    + currency.format(amount) + " (" + currencyId + ")");
        } else if (action.equals("reset")) {
            sender.sendMessage(ChatColor.GREEN + verb + " " + targetName + "'s " + currency.getDisplayName()
                    + " balance to its starting value.");
            transactionLogger.log("ADMIN RESET by " + senderName + " on " + targetName + " (" + currencyId + ")");
        } else {
            sender.sendMessage(ChatColor.GREEN + verb + " " + currency.format(amount) + " "
                    + (action.equals("give") ? "to" : "from") + " " + targetName + ".");
            transactionLogger.log("ADMIN " + action.toUpperCase() + " by " + senderName + " "
                    + (action.equals("give") ? "to" : "from") + " " + targetName + " : "
                    + currency.format(amount) + " (" + currencyId + ")");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "CoinForge Commands");
        sender.sendMessage(ChatColor.YELLOW + "/coins" + ChatColor.GRAY + " - view all your balances");
        sender.sendMessage(ChatColor.YELLOW + "/coins gui" + ChatColor.GRAY + " - open the balance/leaderboard menu");
        sender.sendMessage(ChatColor.YELLOW + "/coins <currency>" + ChatColor.GRAY + " - view one balance");
        sender.sendMessage(ChatColor.YELLOW + "/coins pay <player> <currency> <amount>" + ChatColor.GRAY + " - pay another player");
        sender.sendMessage(ChatColor.YELLOW + "/coins top <currency>" + ChatColor.GRAY + " - top balances (opens a menu for players)");
        sender.sendMessage(ChatColor.YELLOW + "/coins exchange <from> <to> <amount>" + ChatColor.GRAY + " - convert coins");
        if (sender.hasPermission("coinforge.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/coins admin <give|take|set|reset> <player> <currency> [amount]");
            sender.sendMessage(ChatColor.YELLOW + "/coins reload" + ChatColor.GRAY + " - reload config.yml");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        List<String> currencyIds = new ArrayList<>(currencyManager.getAll().keySet());

        if (args.length == 1) {
            options.addAll(Arrays.asList("gui", "pay", "top", "exchange", "help"));
            if (sender.hasPermission("coinforge.admin")) {
                options.addAll(Arrays.asList("admin", "reload"));
            }
            options.addAll(currencyIds);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("pay")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            } else if (sub.equals("top") || sub.equals("exchange")) {
                options.addAll(currencyIds);
            } else if (sub.equals("admin")) {
                options.addAll(Arrays.asList("give", "take", "set", "reset"));
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("pay") || sub.equals("exchange")) {
                options.addAll(currencyIds);
            } else if (sub.equals("admin")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            options.addAll(currencyIds);
        }

        String current = args[args.length - 1].toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(current))
                .collect(Collectors.toList());
    }
}
