package dev.shopforge.plugin.commands;

import dev.shopforge.plugin.ShopForge;
import dev.shopforge.plugin.gui.ShopGuiManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopForge plugin;
    private final ShopGuiManager guiManager;

    public ShopCommand(ShopForge plugin, ShopGuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("shopforge.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                return true;
            }
            plugin.reload();
            sender.sendMessage(ChatColor.GREEN + "ShopForge configuration reloaded.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("sellhand")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can sell items.");
                return true;
            }
            if (!sender.hasPermission("shopforge.use")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                return true;
            }
            plugin.sellHeldItem((Player) sender);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the shop.");
            return true;
        }
        if (!sender.hasPermission("shopforge.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }

        guiManager.openMain((Player) sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("sellhand"));
            if (sender.hasPermission("shopforge.admin")) {
                options.add("reload");
            }
            String current = args[0].toLowerCase();
            return options.stream().filter(o -> o.startsWith(current)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
